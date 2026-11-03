package com.seoulchonnom.aggregate.external.datagokr;

import static com.seoulchonnom.aggregate.external.datagokr.TradeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.ExpectedCount.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.spec.house.entity.vo.DealingType;

@ExtendWith(OutputCaptureExtension.class)
class MolitTradeGatewayTest {
	private static final String BASE = "https://molit.test";
	private static final YearMonth YM = YearMonth.of(2026, 9);
	/** Decoding 키는 +, /, = 를 포함한다. */
	private static final String DECODING_KEY = "ab+cd/ef==";

	private MockRestServiceServer server;
	private MolitTradeGateway gateway;
	private final List<Duration> sleeps = new ArrayList<>();

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new MolitTradeGateway(builder.build(), DECODING_KEY, sleeps::add);
	}

	private CallBudget budget(int limit) {
		return new CallBudget("trade", limit, 0);
	}

	private void respond(String body) {
		server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void fetchAll_shouldEncodeDecodingKeyExactlyOnceAndSendParams() {
		server.expect(requestTo(BASE + "/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
			+ "?serviceKey=ab%2Bcd%2Fef%3D%3D&LAWD_CD=11680&DEAL_YMD=202609&pageNo=1&numOfRows=1000&_type=json"))
			.andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		server.verify();
	}

	@Test
	void fetchAll_shouldConvertRealRowsFromMixedNumberAndStringFields() {
		respond(page(3, 1, ROW_GAEPO, ROW_DOGOK, ROW_WOLSEONG));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.complete()).isTrue();
		assertThat(result.skippedRows()).isZero();
		assertThat(result.records()).hasSize(3);
		TradeRecord gaepo = result.records().get(0);
		assertThat(gaepo.aptSeq()).isEqualTo("11680-283");
		assertThat(gaepo.lawdCd()).isEqualTo("11680");
		assertThat(gaepo.umdCd()).isEqualTo("10300");
		assertThat(gaepo.umdName()).isEqualTo("개포동");
		assertThat(gaepo.bonbun()).isEqualTo("0652");
		assertThat(gaepo.bubun()).isEqualTo("0000");
		assertThat(gaepo.aptName()).isEqualTo("개포우성3차");
		assertThat(gaepo.roadName()).isEqualTo("개포로");
		assertThat(gaepo.roadBonbun()).isEqualTo("307");
		assertThat(gaepo.roadBubun()).isEqualTo("0");
		assertThat(gaepo.amountManwon()).isEqualTo(295000L);
		assertThat(gaepo.dealDate()).isEqualTo(LocalDate.of(2026, 9, 22));
		assertThat(gaepo.exclusiveArea()).isEqualByComparingTo("104.43");
		assertThat(gaepo.floor()).isEqualTo(7);
		assertThat(gaepo.aptDong()).isNull();
		assertThat(gaepo.buildYear()).isEqualTo(1984);
		assertThat(gaepo.cancelled()).isFalse();
		assertThat(gaepo.cancelledDate()).isNull();
		assertThat(gaepo.dealingType()).isEqualTo(DealingType.BROKER);
		TradeRecord dogok = result.records().get(1);
		assertThat(dogok.dealingType()).isEqualTo(DealingType.DIRECT);
		assertThat(dogok.exclusiveArea()).isEqualByComparingTo(new BigDecimal("181.38"));
		assertThat(dogok.umdCd()).isEqualTo("11800");
		assertThat(result.records().get(2).bubun()).isEqualTo("0016");
	}

	@Test
	void fetchAll_shouldReadCancelledRowWithNumericFieldsAndShortDate() {
		respond(page(1, 1, ROW_CANCELLED));

		TradeRecord record = gateway.fetchAll("11680", YM, budget(10)).records().get(0);

		assertThat(record.cancelled()).isTrue();
		assertThat(record.cancelledDate()).isEqualTo(LocalDate.of(2026, 8, 28));
		assertThat(record.bonbun()).isEqualTo("0652");
		assertThat(record.bubun()).isEqualTo("0000");
		assertThat(record.aptDong()).isEqualTo("101");
		assertThat(record.floor()).isEqualTo(-1);
		assertThat(record.dealingType()).isEqualTo(DealingType.UNKNOWN);
		assertThat(record.exclusiveArea()).isEqualByComparingTo("104.43");
	}

	@Test
	void fetchAll_shouldKeepCancelledFlagWhenCancelDateIsUnparseable() {
		respond(page(1, 1, ROW_CANCELLED.replace("26.08.28", "??")));

		TradeRecord record = gateway.fetchAll("11680", YM, budget(10)).records().get(0);

		assertThat(record.cancelled()).isTrue();
		assertThat(record.cancelledDate()).isNull();
	}

	@Test
	void fetchAll_shouldHandleSingleItemObject() {
		respond(SINGLE_ROW_AS_OBJECT);

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.records()).hasSize(1);
		assertThat(result.complete()).isTrue();
	}

	@Test
	void fetchAll_shouldReturnEmptyCompleteResultWhenTotalCountIsZero() {
		respond(EMPTY_ITEMS);

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(result.complete()).isTrue();
		assertThat(result.records()).isEmpty();
	}

	@Test
	void fetchAll_shouldSkipRowsMissingRequiredValuesAndCountThem() {
		respond(page(3, 1, ROW_GAEPO, ROW_DOGOK.replace("\"310,000\"", "\" \""),
			ROW_WOLSEONG.replace("\"excluUseAr\":81.79", "\"excluUseAr\":\"abc\"")));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.records()).hasSize(1);
		assertThat(result.skippedRows()).isEqualTo(2);
		assertThat(result.complete()).isTrue();
	}

	@Test
	void fetchAll_shouldFollowPagesUntilTotalCountIsCovered() {
		server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(page(1500, 1, ROW_GAEPO),
			MediaType.APPLICATION_JSON));
		server.expect(requestTo(containsString("pageNo=2"))).andRespond(withSuccess(page(1500, 2, ROW_DOGOK),
			MediaType.APPLICATION_JSON));
		CallBudget budget = budget(10);

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget);

		assertThat(result.records()).extracting(TradeRecord::aptSeq).containsExactly("11680-283", "11680-3741");
		assertThat(result.complete()).isTrue();
		assertThat(budget.used()).isEqualTo(2);
		server.verify();
	}

	@Test
	void fetchAll_shouldNotBeCompleteWhenLaterPageFails() {
		server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(page(1500, 1, ROW_GAEPO),
			MediaType.APPLICATION_JSON));
		server.expect(times(4), requestTo(containsString("pageNo=2")))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(GATEWAY_TIMEOUT));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(20));

		assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(result.complete()).isFalse();
		assertThat(result.records()).hasSize(1);
	}

	@Test
	void fetchAll_shouldRetryAfterOneTwoFourSecondsThenFail() {
		server.expect(times(4), requestTo(startsWith(BASE)))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(GATEWAY_TIMEOUT));
		CallBudget budget = budget(10);

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget);

		assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
		assertThat(budget.used()).isEqualTo(4);
		server.verify();
	}

	@Test
	void fetchAll_shouldSucceedWhenRetrySucceeds() {
		server.expect(requestTo(startsWith(BASE)))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(GATEWAY_TIMEOUT));
		respond(page(1, 1, ROW_GAEPO));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(result.records()).hasSize(1);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
	}

	@Test
	void fetchAll_shouldRetryNetworkErrorsAndNonJsonBodies() {
		server.expect(requestTo(startsWith(BASE))).andRespond(request -> {
			throw new java.io.IOException("connection reset");
		});
		server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess("<html/>", MediaType.TEXT_HTML));
		respond(page(1, 1, ROW_GAEPO));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.OK);
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void fetchAll_shouldNotRetryWhenKeyIsRejected() {
		server.expect(times(1), requestTo(startsWith(BASE)))
			.andRespond(withSuccess(gatewayError("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30"),
				MediaType.APPLICATION_JSON));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.KEY_REJECTED);
		assertThat(sleeps).isEmpty();
		server.verify();
	}

	@Test
	void fetchAll_shouldNotRetryWhenQuotaExceeded() {
		server.expect(times(1), requestTo(startsWith(BASE)))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
				.body(gatewayError("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "22")));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.QUOTA_EXCEEDED);
		assertThat(sleeps).isEmpty();
		server.verify();
	}

	@Test
	void fetchAll_shouldNotCallWhenBudgetIsExhausted() {
		TradeFetchResult result = gateway.fetchAll("11680", YM, new CallBudget("trade", 3, 3));

		assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
		assertThat(result.complete()).isFalse();
		server.verify();
	}

	@Test
	void fetchAll_shouldStopWhenBudgetRunsOutBetweenPages() {
		server.expect(requestTo(containsString("pageNo=1"))).andRespond(withSuccess(page(3000, 1, ROW_GAEPO),
			MediaType.APPLICATION_JSON));

		TradeFetchResult result = gateway.fetchAll("11680", YM, budget(1));

		assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
		assertThat(result.complete()).isFalse();
		assertThat(result.records()).hasSize(1);
	}

	@Test
	void fetchAll_shouldNeverLogKeyUrlOrBody(CapturedOutput output) {
		server.expect(times(4), requestTo(startsWith(BASE)))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(GATEWAY_TIMEOUT));
		server.expect(requestTo(startsWith(BASE))).andRespond(request -> {
			throw new java.io.IOException("failed " + request.getURI());
		});
		server.expect(requestTo(startsWith(BASE)))
			.andRespond(withSuccess(gatewayError("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30"),
				MediaType.APPLICATION_JSON));

		gateway.fetchAll("11680", YM, budget(10));
		gateway.fetchAll("11680", YM, budget(10));

		assertThat(output.getAll()).contains("실거래 API").doesNotContain("ab+cd").doesNotContain("ab%2Bcd")
			.doesNotContain(BASE).doesNotContain("SERVICETIMEOUT_ERROR").doesNotContain("serviceKey");
	}
}
