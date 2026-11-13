package com.seoulchonnom.aggregate.external.datagokr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.HouseSleeper;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.TradeGateway;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.spec.house.entity.vo.DealingType;

import lombok.extern.slf4j.Slf4j;

/**
 * 국토교통부 아파트 매매 실거래가 상세 자료(RTMSDataSvcAptTradeDev) 어댑터.
 *
 * 서비스 키는 Decoding 키를 URI 템플릿 변수로 넘겨 RestClient가 한 번만 인코딩하게 한다.
 * Encoding 키를 넣으면 %가 두 번 인코딩되어 키 오류가 난다.
 * 요청 URL에 키가 들어 있으므로 예외 메시지·cause·응답 본문은 로그에 남기지 않는다. 예외 클래스명과 우리 쪽 결과만 남긴다.
 */
@Slf4j
public class MolitTradeGateway implements TradeGateway {
	static final String PATH = "/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev";
	static final int PAGE_SIZE = 1000;
	/** 재시도 전 대기. 첫 시도 뒤 최대 3번 다시 한다. */
	static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
		Duration.ofSeconds(4));
	private static final Set<String> NORMAL_CODES = Set.of("000", "00");
	private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
	private static final DateTimeFormatter CANCEL_DAY = DateTimeFormatter.ofPattern("yy.MM.dd");

	private final RestClient restClient;
	private final String serviceKey;
	private final HouseSleeper sleeper;

	/**
	 * 타임아웃과 기본 URL은 RestClient를 만드는 쪽(DataGoKrConfiguration)이 정한다.
	 */
	public MolitTradeGateway(RestClient restClient, String serviceKey, HouseSleeper sleeper) {
		this.restClient = restClient;
		this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
		this.sleeper = sleeper;
	}

	@Override
	public TradeFetchResult fetchAll(String lawdCd, YearMonth dealYm, CallBudget budget) {
		List<TradeRecord> records = new ArrayList<>();
		int skipped = 0;
		int totalPages = 1;
		for (int page = 1; page <= totalPages; page++) {
			PageResult result = fetchPage(lawdCd, dealYm, page, budget);
			if (result.outcome != Outcome.OK) {
				return TradeFetchResult.stopped(result.outcome, records, skipped);
			}
			for (JsonNode item : result.page.items()) {
				TradeRecord record = toRecord(item);
				if (record == null) {
					skipped++;
				} else {
					records.add(record);
				}
			}
			if (page == 1) {
				totalPages = (int)Math.ceil(result.page.totalCount() / (double)PAGE_SIZE);
			}
		}
		return TradeFetchResult.ok(records, skipped);
	}

	private record PageResult(Outcome outcome, DataGoKrResponseReader.Page page) {
	}

	/**
	 * 페이지 하나. 시도마다 예산을 쓴다. 재시도가 필요한데 예산이 없으면 예산 소진으로 멈춘다.
	 */
	private PageResult fetchPage(String lawdCd, YearMonth dealYm, int page, CallBudget budget) {
		for (int attempt = 0; attempt <= RETRY_DELAYS.size(); attempt++) {
			if (!budget.consume()) {
				return new PageResult(Outcome.BUDGET_EXHAUSTED, null);
			}
			DataGoKrResponseReader.Page parsed = callOnce(lawdCd, dealYm, page);
			switch (parsed.result()) {
				case OK, NO_DATA -> {
					return new PageResult(Outcome.OK, parsed);
				}
				case KEY_REJECTED -> {
					log.warn("실거래 API 인증 키 거절: lawdCd={}, dealYm={}", lawdCd, dealYm);
					return new PageResult(Outcome.KEY_REJECTED, null);
				}
				case QUOTA_EXCEEDED -> {
					log.warn("실거래 API 일일 한도 초과: lawdCd={}, dealYm={}", lawdCd, dealYm);
					return new PageResult(Outcome.QUOTA_EXCEEDED, null);
				}
				default -> {
					if (attempt == RETRY_DELAYS.size()) {
						log.warn("실거래 API 호출 실패(재시도 소진): lawdCd={}, dealYm={}, page={}", lawdCd, dealYm, page);
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

	private DataGoKrResponseReader.Page callOnce(String lawdCd, YearMonth dealYm, int page) {
		try {
			// 5xx여도 본문의 오류 봉투(OpenAPI_ServiceResponse)로 분류하려고 상태 코드로 예외를 만들지 않는다.
			String body = restClient.get()
				.uri(PATH + "?serviceKey={serviceKey}&LAWD_CD={lawdCd}&DEAL_YMD={dealYmd}&pageNo={pageNo}"
					+ "&numOfRows={numOfRows}&_type=json", serviceKey, lawdCd, dealYm.format(YM), page, PAGE_SIZE)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
				})
				.body(String.class);
			return DataGoKrResponseReader.read(body, NORMAL_CODES);
		} catch (RuntimeException e) {
			// 메시지에 요청 URL(서비스 키 포함)이 들어가므로 클래스 이름만 남긴다.
			log.warn("실거래 API 호출 예외: {}", e.getClass().getSimpleName());
			return new DataGoKrResponseReader.Page(DataGoKrResultCode.UPSTREAM, 0, List.of());
		}
	}

	/**
	 * 필수값(금액, 날짜, 전용면적)을 해석하지 못하면 null. 그 행만 버린다.
	 */
	static TradeRecord toRecord(JsonNode item) {
		Long amount = parseAmount(DataGoKrResponseReader.text(item, "dealAmount"));
		LocalDate dealDate = parseDealDate(item);
		BigDecimal area = parseArea(DataGoKrResponseReader.text(item, "excluUseAr"));
		if (amount == null || dealDate == null || area == null) {
			return null;
		}
		String cancelType = DataGoKrResponseReader.text(item, "cdealType");
		return new TradeRecord(
			limit(DataGoKrResponseReader.text(item, "aptSeq"), 50),
			digits(DataGoKrResponseReader.text(item, "sggCd"), 5),
			digits(DataGoKrResponseReader.text(item, "umdCd"), 5),
			limit(DataGoKrResponseReader.text(item, "umdNm"), 50),
			digits(DataGoKrResponseReader.text(item, "bonbun"), 4),
			digits(DataGoKrResponseReader.text(item, "bubun"), 4),
			limit(DataGoKrResponseReader.text(item, "aptNm"), 100),
			limit(DataGoKrResponseReader.text(item, "roadNm"), 100),
			stripLeadingZeros(DataGoKrResponseReader.text(item, "roadNmBonbun")),
			stripLeadingZeros(DataGoKrResponseReader.text(item, "roadNmBubun")),
			area,
			amount,
			dealDate,
			parseInt(DataGoKrResponseReader.text(item, "floor")),
			limit(DataGoKrResponseReader.text(item, "aptDong"), 30),
			parseInt(DataGoKrResponseReader.text(item, "buildYear")),
			cancelType != null,
			parseCancelDate(DataGoKrResponseReader.text(item, "cdealDay")),
			DealingType.ofLabel(DataGoKrResponseReader.text(item, "dealingGbn")));
	}

	private static Long parseAmount(String value) {
		if (value == null) {
			return null;
		}
		try {
			long amount = Long.parseLong(value.replaceAll("[\\s,]", ""));
			return amount > 0 ? amount : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate parseDealDate(JsonNode item) {
		Integer year = parseInt(DataGoKrResponseReader.text(item, "dealYear"));
		Integer month = parseInt(DataGoKrResponseReader.text(item, "dealMonth"));
		Integer day = parseInt(DataGoKrResponseReader.text(item, "dealDay"));
		if (year == null || month == null || day == null) {
			return null;
		}
		try {
			return LocalDate.of(year, month, day);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static BigDecimal parseArea(String value) {
		if (value == null) {
			return null;
		}
		try {
			BigDecimal area = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
			return area.signum() > 0 ? area : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate parseCancelDate(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value, CANCEL_DAY);
		} catch (RuntimeException e) {
			// 해제 여부는 cdealType으로 이미 알고 있으므로 날짜만 비운다.
			return null;
		}
	}

	private static Integer parseInt(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 숫자만 남기고 왼쪽을 0으로 채운다. 숫자가 없으면 null.
	 */
	private static String digits(String value, int width) {
		if (value == null) {
			return null;
		}
		String digits = value.replaceAll("\\D", "");
		if (digits.isEmpty()) {
			return null;
		}
		return digits.length() >= width ? digits : "0".repeat(width - digits.length()) + digits;
	}

	/**
	 * 도로명 건물 본번·부번의 앞 0을 없앤다. 전부 0이면 "0"이다.
	 */
	private static String stripLeadingZeros(String value) {
		if (value == null) {
			return null;
		}
		String digits = value.replaceAll("\\D", "");
		if (digits.isEmpty()) {
			return null;
		}
		String stripped = digits.replaceFirst("^0+", "");
		return stripped.isEmpty() ? "0" : stripped;
	}

	private static String limit(String value, int max) {
		if (value == null || value.length() <= max) {
			return value;
		}
		return value.substring(0, max);
	}
}
