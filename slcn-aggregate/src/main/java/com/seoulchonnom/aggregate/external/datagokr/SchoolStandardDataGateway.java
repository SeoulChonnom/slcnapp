package com.seoulchonnom.aggregate.external.datagokr;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.HouseSleeper;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.SchoolGateway;
import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터포털 전국초중등학교위치표준데이터(tn_pubr_public_elesch_mskul_lc_api) 어댑터.
 * 다른 두 API와 호스트가 다르다(api.data.go.kr). 그래서 RestClient를 school-base-url로 따로 만든다.
 * 응답 봉투가 response 없이 최상위 header/body다(DataGoKrResponseReader가 둘 다 읽는다).
 *
 * 서비스 키는 Decoding 키를 URI 템플릿 변수로 넘겨 한 번만 인코딩되게 한다. cddcNm의 한글도 템플릿 변수로 넘긴다.
 * 요청 URL에 키가 들어 있으므로 예외 메시지·본문은 로그에 남기지 않는다.
 */
@Slf4j
public class SchoolStandardDataGateway implements SchoolGateway {
	static final String PATH = "/openapi/tn_pubr_public_elesch_mskul_lc_api";
	static final String SEOUL_EDUCATION_OFFICE = "서울특별시교육청";
	static final String SEOUL_ADDRESS_PREFIX = "서울특별시";
	static final String OPERATING = "운영";
	static final int PAGE_SIZE = 1000;
	/** 서울 1,313건이 2쪽이다. 응답이 이상해 무한히 도는 일을 막는 상한이다. */
	static final int MAX_PAGES = 20;
	static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
		Duration.ofSeconds(4));
	private static final Set<String> NORMAL_CODES = Set.of("00", "000");

	private final RestClient restClient;
	private final String serviceKey;
	private final HouseSleeper sleeper;

	public SchoolStandardDataGateway(RestClient restClient, String serviceKey, HouseSleeper sleeper) {
		this.restClient = restClient;
		this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
		this.sleeper = sleeper;
	}

	@Override
	public SchoolFetchResult fetchAll(CallBudget budget) {
		// 같은 학교 ID가 두 번 오면 마지막 행이 이긴다. 교체 단계의 upsert 키(school_code)와 개수가 맞는다.
		Map<String, SchoolRecord> records = new LinkedHashMap<>();
		int skipped = 0;
		int totalPages = 1;
		for (int page = 1; page <= totalPages; page++) {
			PageResult result = fetchPage(page, budget);
			if (result.outcome != Outcome.OK) {
				return SchoolFetchResult.stopped(result.outcome, skipped);
			}
			for (JsonNode item : result.page.items()) {
				SchoolRecord record = toRecord(item);
				if (record == null) {
					skipped++;
				} else {
					records.put(record.schoolCode(), record);
				}
			}
			if (page == 1) {
				totalPages = Math.min(MAX_PAGES, (int)Math.ceil(result.page.totalCount() / (double)PAGE_SIZE));
			}
		}
		return SchoolFetchResult.ok(List.copyOf(records.values()), skipped);
	}

	private record PageResult(Outcome outcome, DataGoKrResponseReader.Page page) {
	}

	/**
	 * 페이지 하나. 시도마다 예산을 쓴다. 재시도가 필요한데 예산이 없으면 예산 소진으로 멈춘다.
	 */
	private PageResult fetchPage(int page, CallBudget budget) {
		for (int attempt = 0; attempt <= RETRY_DELAYS.size(); attempt++) {
			if (!budget.consume()) {
				return new PageResult(Outcome.BUDGET_EXHAUSTED, null);
			}
			DataGoKrResponseReader.Page parsed = callOnce(page);
			switch (parsed.result()) {
				case OK, NO_DATA -> {
					return new PageResult(Outcome.OK, parsed);
				}
				case KEY_REJECTED -> {
					log.warn("학교 API 인증 키 거절");
					return new PageResult(Outcome.KEY_REJECTED, null);
				}
				case QUOTA_EXCEEDED -> {
					log.warn("학교 API 일일 한도 초과");
					return new PageResult(Outcome.QUOTA_EXCEEDED, null);
				}
				default -> {
					if (attempt == RETRY_DELAYS.size()) {
						log.warn("학교 API 호출 실패(재시도 소진): page={}", page);
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

	private DataGoKrResponseReader.Page callOnce(int page) {
		try {
			// 5xx여도 본문의 오류 봉투로 분류하려고 상태 코드로 예외를 만들지 않는다.
			String body = restClient.get()
				.uri(PATH + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}&type=json"
					+ "&cddcNm={cddcNm}", serviceKey, page, PAGE_SIZE, SEOUL_EDUCATION_OFFICE)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
				})
				.body(String.class);
			return DataGoKrResponseReader.read(body, NORMAL_CODES);
		} catch (RuntimeException e) {
			// 메시지에 요청 URL(서비스 키 포함)이 들어가므로 클래스 이름만 남긴다.
			log.warn("학교 API 호출 예외: {}", e.getClass().getSimpleName());
			return new DataGoKrResponseReader.Page(DataGoKrResultCode.UPSTREAM, 0, List.of());
		}
	}

	/**
	 * 서울 소재 운영 중인 초·중·고만 남긴다. 그 밖이거나 필수값(ID, 이름, 좌표)을 해석하지 못하면 null이다.
	 * 서버 필터(cddcNm)를 걸어도 소재지 주소를 한 번 더 본다. 위경도는 문자열로 온다.
	 */
	static SchoolRecord toRecord(JsonNode item) {
		String address = DataGoKrResponseReader.text(item, "lnmadr");
		if (address == null) {
			address = DataGoKrResponseReader.text(item, "rdnmadr");
		}
		if (address == null || !address.startsWith(SEOUL_ADDRESS_PREFIX)) {
			return null;
		}
		if (!OPERATING.equals(DataGoKrResponseReader.text(item, "operSttus"))) {
			return null;
		}
		SchoolLevel level = SchoolLevel.ofSchoolType(DataGoKrResponseReader.text(item, "schoolSe"));
		String code = DataGoKrResponseReader.text(item, "schoolId");
		String name = DataGoKrResponseReader.text(item, "schoolNm");
		Double latitude = parseCoordinate(DataGoKrResponseReader.text(item, "latitude"), 33, 39);
		Double longitude = parseCoordinate(DataGoKrResponseReader.text(item, "longitude"), 124, 132);
		if (level == null || code == null || name == null || latitude == null || longitude == null) {
			return null;
		}
		return new SchoolRecord(code, name, level, latitude, longitude);
	}

	/** 한반도 범위를 벗어나거나 해석할 수 없으면 null. 0,0 같은 빈 좌표가 최근접 계산에 섞이지 않게 한다. */
	private static Double parseCoordinate(String value, double min, double max) {
		if (value == null) {
			return null;
		}
		try {
			double parsed = Double.parseDouble(value);
			return parsed >= min && parsed <= max ? parsed : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
