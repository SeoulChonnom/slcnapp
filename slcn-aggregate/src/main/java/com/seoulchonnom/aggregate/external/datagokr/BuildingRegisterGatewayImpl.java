package com.seoulchonnom.aggregate.external.datagokr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.seoulchonnom.aggregate.house.collect.BuildingRegisterGateway;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.HouseSleeper;
import com.seoulchonnom.aggregate.house.collect.RecapFailureTracker;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.RegisterSummary;
import com.seoulchonnom.spec.house.entity.vo.FarSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 국토교통부 건축HUB 건축물대장정보 서비스(BldRgstHubService) 어댑터.
 *
 * 총괄표제부를 먼저 보고, 세대수·용적률·건폐율·사용승인일 중 빈 필드(0도 빈 값)가 있으면 표제부로 그 필드만 채운다.
 * 오래된 단지는 총괄표제부가 정상 응답해도 용적률·건폐율이 0이고 사용승인일이 비어 있기 때문이다.
 * 총괄표제부는 재시도하지 않는다. 실패하면 바로 표제부로 넘어가고, 연속 3번 실패하면 이번 실행에서는 건너뛴다.
 *
 * 서비스 키 처리와 로그 규칙은 MolitTradeGateway와 같다(Decoding 키를 템플릿 변수로, 예외 메시지·본문은 로그에 남기지 않는다).
 */
@Slf4j
public class BuildingRegisterGatewayImpl implements BuildingRegisterGateway {
	static final String RECAP_PATH = "/1613000/BldRgstHubService/getBrRecapTitleInfo";
	static final String TITLE_PATH = "/1613000/BldRgstHubService/getBrTitleInfo";
	static final int PAGE_SIZE = 100;
	/** 표제부 페이지 상한. 한 지번에 동이 수백 개인 경우는 없지만 무한 루프를 막는다. */
	static final int MAX_TITLE_PAGES = 5;
	static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
		Duration.ofSeconds(4));
	static final String APARTMENT_PURPOSE = "공동주택";
	private static final Set<String> NORMAL_CODES = Set.of("00", "000");
	private static final DateTimeFormatter APPROVAL_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final RestClient restClient;
	private final String serviceKey;
	private final HouseSleeper sleeper;

	public BuildingRegisterGatewayImpl(RestClient restClient, String serviceKey, HouseSleeper sleeper) {
		this.restClient = restClient;
		this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
		this.sleeper = sleeper;
	}

	/** 지금까지 모은 네 필드. 비어 있는 것만 채운다. */
	private static final class Fields {
		Integer households;
		BigDecimal farPct;
		BigDecimal bcrPct;
		LocalDate approvalDate;
		FarSource farSource;

		boolean complete() {
			return households != null && farPct != null && bcrPct != null && approvalDate != null;
		}

		boolean empty() {
			return households == null && farPct == null && bcrPct == null && approvalDate == null;
		}

		void fill(Integer households, BigDecimal farPct, BigDecimal bcrPct, LocalDate approvalDate,
			FarSource source) {
			if (this.households == null) {
				this.households = households;
			}
			if (this.farPct == null && farPct != null) {
				this.farPct = farPct;
				this.farSource = source;
			}
			if (this.bcrPct == null) {
				this.bcrPct = bcrPct;
			}
			if (this.approvalDate == null) {
				this.approvalDate = approvalDate;
			}
		}

		RegisterSummary toSummary() {
			return new RegisterSummary(households, farPct, bcrPct, approvalDate, farPct == null ? null : farSource);
		}
	}

	@Override
	public RegisterLookupResult find(String lawdCd, String umdCd, String bonbun, String bubun, CallBudget budget,
		RecapFailureTracker tracker) {
		Fields fields = new Fields();

		if (!tracker.shouldSkip()) {
			if (!budget.consume()) {
				return RegisterLookupResult.of(Outcome.BUDGET_EXHAUSTED);
			}
			DataGoKrResponseReader.Page recap = callOnce(RECAP_PATH, lawdCd, umdCd, bonbun, bubun, 1);
			switch (recap.result()) {
				case KEY_REJECTED -> {
					log.warn("건축물대장 API 인증 키 거절");
					return RegisterLookupResult.of(Outcome.KEY_REJECTED);
				}
				case QUOTA_EXCEEDED -> {
					log.warn("건축물대장 API 일일 한도 초과");
					return RegisterLookupResult.of(Outcome.QUOTA_EXCEEDED);
				}
				case UPSTREAM -> tracker.recordUpstreamFailure();
				case NO_DATA -> tracker.recordAnswered();
				case OK -> {
					tracker.recordAnswered();
					if (!recap.items().isEmpty()) {
						JsonNode item = recap.items().get(0);
						fields.fill(positiveInt(item), positiveDecimal(item, "vlRat"), positiveDecimal(item, "bcRat"),
							parseApprovalDay(DataGoKrResponseReader.text(item, "useAprDay")), FarSource.RECAP);
					}
				}
			}
		}

		if (!fields.complete()) {
			RegisterLookupResult stopped = fillFromTitle(lawdCd, umdCd, bonbun, bubun, budget, fields);
			if (stopped != null) {
				return stopped;
			}
		}
		return fields.empty() ? RegisterLookupResult.of(Outcome.NOT_FOUND)
			: RegisterLookupResult.found(fields.toSummary());
	}

	/**
	 * 표제부로 빈 필드를 채운다.
	 *
	 * @return 조회를 이어 갈 수 없으면 그 결과, 채웠거나 표제부에 해당 동이 없으면 null
	 */
	private RegisterLookupResult fillFromTitle(String lawdCd, String umdCd, String bonbun, String bubun,
		CallBudget budget, Fields fields) {
		List<JsonNode> apartments = new ArrayList<>();
		int totalPages = 1;
		for (int page = 1; page <= totalPages; page++) {
			PageResult result = fetchTitlePage(lawdCd, umdCd, bonbun, bubun, page, budget);
			if (result.outcome != null) {
				return RegisterLookupResult.of(result.outcome);
			}
			for (JsonNode item : result.page.items()) {
				if (APARTMENT_PURPOSE.equals(DataGoKrResponseReader.text(item, "mainPurpsCdNm"))) {
					apartments.add(item);
				}
			}
			if (page == 1) {
				totalPages = Math.min(MAX_TITLE_PAGES, (int)Math.ceil(result.page.totalCount() / (double)PAGE_SIZE));
			}
		}
		if (apartments.isEmpty()) {
			return null;
		}
		int households = 0;
		for (JsonNode apartment : apartments) {
			Integer count = positiveInt(apartment);
			households += count == null ? 0 : count;
		}
		JsonNode largest = apartments.stream()
			.max(Comparator.comparing((JsonNode node) -> decimal(DataGoKrResponseReader.text(node, "totArea"))
				, Comparator.nullsFirst(Comparator.naturalOrder())))
			.orElseThrow();
		fields.fill(households > 0 ? households : null, positiveDecimal(largest, "vlRat"),
			positiveDecimal(largest, "bcRat"),
			parseApprovalDay(DataGoKrResponseReader.text(largest, "useAprDay")), FarSource.TITLE);
		return null;
	}

	/**
	 * @param outcome null이면 page가 유효하다.
	 */
	private record PageResult(Outcome outcome, DataGoKrResponseReader.Page page) {
	}

	/**
	 * 표제부 페이지 하나. 시도마다 예산을 쓰고, 일시 오류는 대기 후 다시 한다.
	 */
	private PageResult fetchTitlePage(String lawdCd, String umdCd, String bonbun, String bubun, int page,
		CallBudget budget) {
		for (int attempt = 0; attempt <= RETRY_DELAYS.size(); attempt++) {
			if (!budget.consume()) {
				return new PageResult(Outcome.BUDGET_EXHAUSTED, null);
			}
			DataGoKrResponseReader.Page parsed = callOnce(TITLE_PATH, lawdCd, umdCd, bonbun, bubun, page);
			switch (parsed.result()) {
				case OK, NO_DATA -> {
					return new PageResult(null, parsed);
				}
				case KEY_REJECTED -> {
					log.warn("건축물대장 API 인증 키 거절");
					return new PageResult(Outcome.KEY_REJECTED, null);
				}
				case QUOTA_EXCEEDED -> {
					log.warn("건축물대장 API 일일 한도 초과");
					return new PageResult(Outcome.QUOTA_EXCEEDED, null);
				}
				default -> {
					if (attempt == RETRY_DELAYS.size()) {
						log.warn("건축물대장 표제부 호출 실패(재시도 소진)");
						return new PageResult(Outcome.FAILED, null);
					}
					try {
						sleeper.sleep(RETRY_DELAYS.get(attempt));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return new PageResult(Outcome.FAILED, null);
					}
				}
			}
		}
		return new PageResult(Outcome.FAILED, null);
	}

	private DataGoKrResponseReader.Page callOnce(String path, String lawdCd, String umdCd, String bonbun, String bubun,
		int page) {
		try {
			// 5xx여도 본문의 오류 봉투(OpenAPI_ServiceResponse)로 분류하려고 상태 코드로 예외를 만들지 않는다.
			String body = restClient.get()
				.uri(path + "?serviceKey={serviceKey}&sigunguCd={sigunguCd}&bjdongCd={bjdongCd}&platGbCd=0"
					+ "&bun={bun}&ji={ji}&numOfRows={numOfRows}&pageNo={pageNo}&_type=json", serviceKey, lawdCd, umdCd,
					bonbun, bubun, PAGE_SIZE, page)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
				})
				.body(String.class);
			return DataGoKrResponseReader.read(body, NORMAL_CODES);
		} catch (RuntimeException e) {
			// 메시지에 요청 URL(서비스 키 포함)이 들어가므로 클래스 이름만 남긴다.
			log.warn("건축물대장 API 호출 예외: {}", e.getClass().getSimpleName());
			return new DataGoKrResponseReader.Page(DataGoKrResultCode.UPSTREAM, 0, List.of());
		}
	}

	/** 세대수. 0은 빈 값이다. */
	private static Integer positiveInt(JsonNode item) {
		BigDecimal value = decimal(DataGoKrResponseReader.text(item, "hhldCnt"));
		return value != null && value.signum() > 0 ? value.intValue() : null;
	}

	/** 용적률·건폐율. 0은 빈 값이다. */
	private static BigDecimal positiveDecimal(JsonNode item, String field) {
		BigDecimal value = decimal(DataGoKrResponseReader.text(item, field));
		return value != null && value.signum() > 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
	}

	private static BigDecimal decimal(String value) {
		if (value == null) {
			return null;
		}
		try {
			return new BigDecimal(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** yyyyMMdd. 빈 값이나 해석할 수 없는 값은 null이다. */
	private static LocalDate parseApprovalDay(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value, APPROVAL_DAY);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
