package com.seoulchonnom.aggregate.external.datagokr;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

@ExtendWith(OutputCaptureExtension.class)
class SchoolStandardDataGatewayTest {
	private static final String BASE = "https://school.test";
	private static final String DECODING_KEY = "ab+cd/ef==";

	/** 2026-10-21 실제 호출 응답의 필드 구성(최상위 header/body, 위경도 문자열)을 따른 행. */
	private static String row(String id, String name, String type, String status, String lotAddress) {
		return """
			{"schoolId":"%s","schoolNm":"%s","schoolSe":"%s","fondDate":"2005-10-04","fondType":"공립","bnhhSe":"본교",\
			"operSttus":"%s","lnmadr":"%s","rdnmadr":"%s 도로 1","cddcCode":7310000,"cddcNm":"서울특별시교육청",\
			"edcSport":7331000,"edcSportNm":"서울특별시강남교육지원청","creatDate":"2013-11-29","changeDate":"2026-07-08",\
			"latitude":"37.49113468","longitude":"126.7368608","referenceDate":"2026-10-01","insttCode":"B554398",\
			"insttNm":"한국교육시설안전원"}""".formatted(id, name, type, status, lotAddress, lotAddress);
	}

	private static String page(int totalCount, int pageNo, String... rows) {
		return """
			{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[%s]},\
			"numOfRows":1000,"pageNo":%d,"totalCount":%d}}""".formatted(String.join(",", rows), pageNo, totalCount);
	}

	private MockRestServiceServer server;
	private SchoolStandardDataGateway gateway;
	private final List<Duration> sleeps = new ArrayList<>();

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new SchoolStandardDataGateway(builder.build(), DECODING_KEY, sleeps::add);
	}

	private CallBudget budget(int limit) {
		return new CallBudget("school", limit, 0);
	}

	@Test
	void fetchAll_shouldEncodeKeyAndKoreanParamExactlyOnceAndSendParams() {
		server.expect(requestTo(BASE + "/openapi/tn_pubr_public_elesch_mskul_lc_api"
			+ "?serviceKey=ab%2Bcd%2Fef%3D%3D&pageNo=1&numOfRows=1000&type=json"
			+ "&cddcNm=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C%EA%B5%90%EC%9C%A1%EC%B2%AD"))
			.andRespond(withSuccess(page(1, 1, row("B1", "서울초", "초등학교", "운영", "서울특별시 강남구 개포동 1")),
				MediaType.APPLICATION_JSON));

		SchoolFetchResult result = gateway.fetchAll(budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(result.records()).hasSize(1);
		server.verify();
	}

	@Test
	void fetchAll_shouldConvertRowAndKeepOnlyOperatingSeoulElementaryMiddleHigh() {
		server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(page(7, 1,
			row("B1", "서울초등학교", "초등학교", "운영", "서울특별시 강남구 개포동 1"),
			row("B2", "서울중학교", "중학교", "운영", "서울특별시 강남구 개포동 2"),
			row("B3", "서울고등학교", "고등학교", "운영", "서울특별시 서초구 방배동 3"),
			row("B4", "서울특수학교", "특수학교", "운영", "서울특별시 강남구 개포동 4"),
			row("B5", "폐교된초등학교", "초등학교", "폐교", "서울특별시 강남구 개포동 5"),
			row("B6", "인천부흥고등학교", "고등학교", "운영", "인천광역시 부평구 부개동 120-547"),
			row("B7", "휴업중학교", "중학교", "휴업", "서울특별시 강남구 개포동 7")), MediaType.APPLICATION_JSON));

		SchoolFetchResult result = gateway.fetchAll(budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(result.records()).extracting(SchoolRecord::schoolCode).containsExactly("B1", "B2", "B3");
		assertThat(result.records()).extracting(SchoolRecord::level)
			.containsExactly(SchoolLevel.E, SchoolLevel.M, SchoolLevel.H);
		assertThat(result.skippedRows()).isEqualTo(4);
		SchoolRecord first = result.records().get(0);
		assertThat(first.name()).isEqualTo("서울초등학교");
		assertThat(first.latitude()).isEqualTo(37.49113468);
		assertThat(first.longitude()).isEqualTo(126.7368608);
	}

	@Test
	void fetchAll_shouldDropRowsWithoutUsableCoordinates() {
		String noCoordinates = row("B1", "좌표없음초", "초등학교", "운영", "서울특별시 강남구 개포동 1")
			.replace("\"latitude\":\"37.49113468\"", "\"latitude\":\" \"");
		String zeroCoordinates = row("B2", "영점초", "초등학교", "운영", "서울특별시 강남구 개포동 2")
			.replace("\"latitude\":\"37.49113468\"", "\"latitude\":\"0\"");
		server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(page(2, 1, noCoordinates, zeroCoordinates),
			MediaType.APPLICATION_JSON));

		SchoolFetchResult result = gateway.fetchAll(budget(10));

		assertThat(result.records()).isEmpty();
		assertThat(result.skippedRows()).isEqualTo(2);
	}

	@Test
	void fetchAll_shouldReadAllPagesWhenTotalCountExceedsOnePage() {
		server.expect(requestTo(containsString("pageNo=1")))
			.andRespond(withSuccess(page(1313, 1, row("B1", "서울초", "초등학교", "운영", "서울특별시 강남구 개포동 1")),
				MediaType.APPLICATION_JSON));
		server.expect(requestTo(containsString("pageNo=2")))
			.andRespond(withSuccess(page(1313, 2, row("B2", "서울중", "중학교", "운영", "서울특별시 강남구 개포동 2")),
				MediaType.APPLICATION_JSON));
		CallBudget budget = budget(10);

		SchoolFetchResult result = gateway.fetchAll(budget);

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(result.records()).extracting(SchoolRecord::schoolCode).containsExactly("B1", "B2");
		assertThat(budget.used()).isEqualTo(2);
		server.verify();
	}

	@Test
	void fetchAll_shouldKeepLastRowWhenSchoolIdRepeats() {
		server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(page(2, 1,
			row("B1", "옛이름초", "초등학교", "운영", "서울특별시 강남구 개포동 1"),
			row("B1", "새이름초", "초등학교", "운영", "서울특별시 강남구 개포동 1")), MediaType.APPLICATION_JSON));

		SchoolFetchResult result = gateway.fetchAll(budget(10));

		assertThat(result.records()).extracting(SchoolRecord::name).containsExactly("새이름초");
	}

	@Test
	void fetchAll_shouldReturnBudgetExhaustedWithoutCallingWhenNoBudget() {
		SchoolFetchResult result = gateway.fetchAll(budget(0));

		assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
		assertThat(result.records()).isEmpty();
		server.verify();
	}

	@Test
	void fetchAll_shouldNotReturnPartialRecordsWhenSecondPageFails() {
		server.expect(requestTo(containsString("pageNo=1")))
			.andRespond(withSuccess(page(1313, 1, row("B1", "서울초", "초등학교", "운영", "서울특별시 강남구 개포동 1")),
				MediaType.APPLICATION_JSON));
		for (int i = 0; i < 4; i++) {
			server.expect(requestTo(containsString("pageNo=2"))).andRespond(withServerError());
		}

		SchoolFetchResult result = gateway.fetchAll(budget(20));

		assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(result.records()).isEmpty();
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
	}

	@Test
	void fetchAll_shouldNotRetryKeyRejection() {
		server.expect(requestTo(startsWith(BASE))).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
			.contentType(MediaType.APPLICATION_JSON).body("""
				{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE ERROR",\
				"returnAuthMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnReasonCode":"30"}}}"""));

		SchoolFetchResult result = gateway.fetchAll(budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.KEY_REJECTED);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void fetchAll_shouldNotLeakServiceKeyToLogOnFailure(CapturedOutput output) {
		for (int i = 0; i < 4; i++) {
			server.expect(requestTo(startsWith(BASE))).andRespond(withServerError());
		}

		gateway.fetchAll(budget(10));

		assertThat(output.getAll()).doesNotContain(DECODING_KEY).doesNotContain("ab%2Bcd");
	}
}
