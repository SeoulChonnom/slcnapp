package com.seoulchonnom.aggregate.external.datagokr;

import static com.seoulchonnom.aggregate.external.datagokr.RegisterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.ExpectedCount.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
import com.seoulchonnom.aggregate.house.collect.RecapFailureTracker;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.RegisterSummary;
import com.seoulchonnom.spec.house.entity.vo.FarSource;

@ExtendWith(OutputCaptureExtension.class)
class BuildingRegisterGatewayImplTest {
	private static final String BASE = "https://register.test";
	private static final String RECAP_URL = BASE + BuildingRegisterGatewayImpl.RECAP_PATH;
	private static final String TITLE_URL = BASE + BuildingRegisterGatewayImpl.TITLE_PATH;
	private static final String DECODING_KEY = "ab+cd/ef==";

	private MockRestServiceServer server;
	private BuildingRegisterGatewayImpl gateway;
	private final List<Duration> sleeps = new ArrayList<>();
	private final RecapFailureTracker tracker = new RecapFailureTracker();

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new BuildingRegisterGatewayImpl(builder.build(), DECODING_KEY, sleeps::add);
	}

	private CallBudget budget(int limit) {
		return new CallBudget("buildingRegister", limit, 0);
	}

	private RegisterLookupResult find(CallBudget budget) {
		return gateway.find("11680", "10300", "0652", "0000", budget, tracker);
	}

	private void recapReturns(String body) {
		server.expect(requestTo(startsWith(RECAP_URL))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private void titleReturns(String body) {
		server.expect(requestTo(startsWith(TITLE_URL))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private void timeout(String urlPrefix) {
		server.expect(requestTo(startsWith(urlPrefix)))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(GATEWAY_TIMEOUT));
	}

	@Test
	void find_shouldSendKeyAndLotParamsAndUseOnlyRecapWhenAllFieldsPresent() {
		server.expect(requestTo(RECAP_URL + "?serviceKey=ab%2Bcd%2Fef%3D%3D&sigunguCd=11680&bjdongCd=10300&platGbCd=0"
			+ "&bun=0652&ji=0000&numOfRows=100&pageNo=1&_type=json"))
			.andRespond(withSuccess(envelope(1, RECAP_ITEM_FULL), MediaType.APPLICATION_JSON));
		CallBudget budget = budget(10);

		RegisterLookupResult result = find(budget);

		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		RegisterSummary summary = result.summary();
		assertThat(summary.households()).isEqualTo(1200);
		assertThat(summary.farPct()).isEqualByComparingTo("249.99");
		assertThat(summary.bcrPct()).isEqualByComparingTo("19.37");
		assertThat(summary.approvalDate()).isEqualTo(LocalDate.of(2008, 5, 30));
		assertThat(summary.farSource()).isEqualTo(FarSource.RECAP);
		assertThat(budget.used()).isEqualTo(1);
		server.verify();
	}

	@Test
	void find_shouldFillOnlyEmptyFieldsFromTitleWhenRecapHasOnlyHouseholds() {
		recapReturns(envelope(1, RECAP_ITEM_OLD));
		titleReturns(envelope(4, TITLE_SHOP, TITLE_APT_LARGE, TITLE_APT_SMALL, TITLE_APT_EMPTY_VALUES));
		CallBudget budget = budget(10);

		RegisterSummary summary = find(budget).summary();

		// 세대수는 총괄표제부의 405를 그대로 둔다(표제부 합 505로 덮지 않는다).
		assertThat(summary.households()).isEqualTo(405);
		// 나머지는 공동주택 동 중 연면적이 가장 큰 동(103동은 값이 비었고 연면적이 40000이라 가장 크다).
		assertThat(summary.farPct()).isNull();
		assertThat(summary.bcrPct()).isNull();
		assertThat(summary.approvalDate()).isNull();
		assertThat(summary.farSource()).isNull();
		assertThat(budget.used()).isEqualTo(2);
	}

	@Test
	void find_shouldTakeNonApartmentBuildingsOutAndUseLargestApartmentBuilding() {
		recapReturns(envelope(1, RECAP_ITEM_OLD));
		titleReturns(envelope(3, TITLE_SHOP, TITLE_APT_LARGE, TITLE_APT_SMALL));

		RegisterSummary summary = find(budget(10)).summary();

		assertThat(summary.households()).isEqualTo(405);
		assertThat(summary.farPct()).isEqualByComparingTo("249.50");
		assertThat(summary.bcrPct()).isEqualByComparingTo("18.20");
		assertThat(summary.approvalDate()).isEqualTo(LocalDate.of(1984, 8, 31));
		assertThat(summary.farSource()).isEqualTo(FarSource.TITLE);
	}

	@Test
	void find_shouldSumHouseholdsOfApartmentBuildingsWhenRecapMissing() {
		recapReturns(envelope(0));
		titleReturns(envelope(3, TITLE_SHOP, TITLE_APT_LARGE, TITLE_APT_SMALL));

		RegisterSummary summary = find(budget(10)).summary();

		assertThat(summary.households()).isEqualTo(405);
		assertThat(summary.farPct()).isEqualByComparingTo("249.50");
		assertThat(summary.farSource()).isEqualTo(FarSource.TITLE);
	}

	@Test
	void find_shouldKeepRecapFarAndSourceAndFillOnlyApprovalDateFromTitle() {
		recapReturns(envelope(1, RECAP_ITEM_FAR_ONLY));
		titleReturns(envelope(1, TITLE_APT_LARGE));

		RegisterSummary summary = find(budget(10)).summary();

		assertThat(summary.households()).isEqualTo(300);
		assertThat(summary.farPct()).isEqualByComparingTo(new BigDecimal("199.50"));
		assertThat(summary.farSource()).isEqualTo(FarSource.RECAP);
		// 건폐율과 사용승인일은 표제부에서 채운다.
		assertThat(summary.bcrPct()).isEqualByComparingTo("18.20");
		assertThat(summary.approvalDate()).isEqualTo(LocalDate.of(1984, 8, 31));
	}

	@Test
	void find_shouldTreatZeroAndBlankAsEmptyAndReturnNotFoundWhenAllFourEmpty() {
		recapReturns(envelope(1, """
			{"regstrKindCdNm":"총괄표제부","bcRat":0,"vlRat":0,"hhldCnt":0,"useAprDay":" "}"""));
		titleReturns(envelope(1, TITLE_SHOP));

		RegisterLookupResult result = find(budget(10));

		assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
		assertThat(result.summary()).isNull();
	}

	@Test
	void find_shouldReturnNotFoundWhenBothApisHaveNoData() {
		recapReturns(envelope(0));
		titleReturns(envelope(0));

		assertThat(find(budget(10)).outcome()).isEqualTo(Outcome.NOT_FOUND);
	}

	@Test
	void find_shouldFallBackToTitleWithoutRetryWhenRecapTimesOut() {
		timeout(RECAP_URL);
		titleReturns(envelope(2, TITLE_APT_LARGE, TITLE_APT_SMALL));
		CallBudget budget = budget(10);

		RegisterLookupResult result = find(budget);

		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		assertThat(result.summary().farSource()).isEqualTo(FarSource.TITLE);
		assertThat(sleeps).isEmpty();
		assertThat(budget.used()).isEqualTo(2);
		assertThat(tracker.streak()).isEqualTo(1);
		server.verify();
	}

	@Test
	void find_shouldSkipRecapAfterThreeConsecutiveUpstreamFailures() {
		tracker.recordUpstreamFailure();
		tracker.recordUpstreamFailure();
		tracker.recordUpstreamFailure();
		titleReturns(envelope(1, TITLE_APT_LARGE));
		CallBudget budget = budget(10);

		RegisterLookupResult result = find(budget);

		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		assertThat(budget.used()).isEqualTo(1);
		// 총괄표제부 요청은 한 번도 나가지 않았다.
		server.verify();
	}

	@Test
	void find_shouldCountThirdFailureAcrossLookupsThenSkipRecap() {
		for (int i = 0; i < 3; i++) {
			timeout(RECAP_URL);
			titleReturns(envelope(1, TITLE_APT_LARGE));
		}
		for (int i = 0; i < 3; i++) {
			assertThat(find(budget(10)).outcome()).isEqualTo(Outcome.FOUND);
		}
		assertThat(tracker.shouldSkip()).isTrue();
	}

	@Test
	void find_shouldResetFailureStreakWhenRecapAnswers() {
		tracker.recordUpstreamFailure();
		tracker.recordUpstreamFailure();
		recapReturns(envelope(1, RECAP_ITEM_FULL));

		find(budget(10));

		assertThat(tracker.streak()).isZero();
	}

	@Test
	void find_shouldRetryTitleWithBackoffThenFailWhenAlwaysTimingOut() {
		recapReturns(envelope(0));
		for (int i = 0; i < 4; i++) {
			timeout(TITLE_URL);
		}
		CallBudget budget = budget(20);

		RegisterLookupResult result = find(budget);

		assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
		assertThat(budget.used()).isEqualTo(5);
	}

	@Test
	void find_shouldFailInsteadOfPartialResultWhenTitleFailsAfterRecapGaveSomeFields() {
		recapReturns(envelope(1, RECAP_ITEM_OLD));
		for (int i = 0; i < 4; i++) {
			timeout(TITLE_URL);
		}

		assertThat(find(budget(20)).outcome()).isEqualTo(Outcome.FAILED);
	}

	@Test
	void find_shouldStopWithBudgetExhaustedBeforeAnyCallWhenNoBudget() {
		RegisterLookupResult result = find(budget(0));

		assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
		server.verify();
	}

	@Test
	void find_shouldReportBudgetExhaustedWhenTitleCannotBeCalled() {
		recapReturns(envelope(1, RECAP_ITEM_OLD));

		assertThat(find(budget(1)).outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
	}

	@Test
	void find_shouldReturnKeyRejectedWithoutRetry() {
		server.expect(requestTo(startsWith(RECAP_URL)))
			.andRespond(withSuccess(TradeFixtures.gatewayError("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30"),
				MediaType.APPLICATION_JSON));

		assertThat(find(budget(10)).outcome()).isEqualTo(Outcome.KEY_REJECTED);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void find_shouldReturnQuotaExceededFromTitle() {
		recapReturns(envelope(0));
		server.expect(requestTo(startsWith(TITLE_URL)))
			.andRespond(withSuccess(TradeFixtures.gatewayError("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "22"),
				MediaType.APPLICATION_JSON));

		assertThat(find(budget(10)).outcome()).isEqualTo(Outcome.QUOTA_EXCEEDED);
	}

	@Test
	void find_shouldNotLogServiceKeyWhenCallThrows(CapturedOutput output) {
		server.expect(requestTo(startsWith(RECAP_URL)))
			.andRespond(request -> {
				throw new IOException("failed " + request.getURI());
			});
		titleReturns(envelope(1, TITLE_APT_LARGE));

		find(budget(10));

		assertThat(output.getAll()).doesNotContain("ab+cd").doesNotContain("ab%2Bcd");
	}
}
