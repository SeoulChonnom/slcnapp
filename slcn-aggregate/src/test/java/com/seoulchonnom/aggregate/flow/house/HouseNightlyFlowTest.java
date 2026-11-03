package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.BuildingRegisterGateway;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.ComplexGeocoder;
import com.seoulchonnom.aggregate.house.collect.DisabledRedevelopmentGateway;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.RedevelopmentGateway;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.TradeGateway;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseCollectUnitLogic;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.aggregate.house.logic.HouseStatLogic;
import com.seoulchonnom.aggregate.house.logic.HouseTradeLogic;
import com.seoulchonnom.aggregate.house.logic.StatRebuildResult;
import com.seoulchonnom.aggregate.house.logic.TradeApplyResult;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

class HouseNightlyFlowTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

	private final TradeGateway tradeGateway = mock(TradeGateway.class);
	private final HouseCollectUnitLogic unitLogic = mock(HouseCollectUnitLogic.class);
	private final HouseTradeLogic tradeLogic = mock(HouseTradeLogic.class);
	private final HouseBatchRunLogic batchRunLogic = mock(HouseBatchRunLogic.class);
	private final BuildingRegisterGateway registerGateway = mock(BuildingRegisterGateway.class);
	private final HouseStatLogic statLogic = mock(HouseStatLogic.class);
	private final SchoolSyncStep schoolSyncStep = mock(SchoolSyncStep.class);
	private final RedevelopmentGateway redevelopmentGateway = new DisabledRedevelopmentGateway();
	private final ComplexGeocoder geocoder = mock(ComplexGeocoder.class);
	private final HouseComplexLogic complexLogic = mock(HouseComplexLogic.class);
	private final HouseProperties properties = new HouseProperties();
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:00:00Z"), ZoneId.of("Asia/Seoul"));
	private HouseNightlyFlow flow;

	@BeforeEach
	void setUp() {
		ComplexEnrichStep enrichStep = new ComplexEnrichStep(geocoder, registerGateway, complexLogic, batchRunLogic,
			properties);
		flow = new HouseNightlyFlow(tradeGateway, unitLogic, tradeLogic, batchRunLogic,
			new BatchStepRecorder(batchRunLogic, clock), properties, enrichStep, statLogic, schoolSyncStep,
			redevelopmentGateway);
		when(schoolSyncStep.run(any(), anyInt(), any())).thenReturn(StepOutcome.of(BatchRunStatus.SKIPPED));
		when(statLogic.rebuild(any())).thenReturn(new StatRebuildResult(100, 3, 40));
		when(tradeGateway.available()).thenReturn(true);
		when(registerGateway.available()).thenReturn(true);
		when(batchRunLogic.usedApiCalls(any(), any())).thenReturn(0L);
		when(batchRunLogic.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(tradeLogic.applyUnit(any(), any(), any()))
			.thenReturn(new TradeApplyResult(10, 6, 1, 3, 2, 1, 0, 1, 0, false));
	}

	private HouseCollectUnit unit(String lawdCd, String ym) {
		return HouseCollectUnit.builder().lawdCd(lawdCd).dealYm(ym).status(CollectUnitStatus.NEVER).build();
	}

	/** 게이트웨이 호출마다 예산 1회를 쓰고 주어진 결과를 돌려준다. */
	private void gatewayReturns(TradeFetchResult... results) {
		List<TradeFetchResult> queue = new ArrayList<>(List.of(results));
		when(tradeGateway.fetchAll(any(), any(), any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(2);
			budget.consume();
			return queue.size() > 1 ? queue.remove(0) : queue.get(0);
		});
	}

	private List<HouseBatchRun> savedRuns() {
		ArgumentCaptor<HouseBatchRun> captor = ArgumentCaptor.forClass(HouseBatchRun.class);
		verify(batchRunLogic, atLeast(0)).save(captor.capture());
		return captor.getAllValues();
	}

	private HouseBatchRun savedRun(BatchStep step) {
		return savedRuns().stream().filter(run -> run.getStep() == step).findFirst().orElseThrow();
	}

	@Test
	void run_shouldSkipCollectStepsButStillBuildStatsWhenNoServiceKey() {
		when(tradeGateway.available()).thenReturn(false);

		flow.run(TODAY);

		assertThat(savedRuns()).extracting(HouseBatchRun::getStep)
			.containsExactly(BatchStep.COLLECT_UNIT_PREPARE, BatchStep.TRADE_COLLECT, BatchStep.COMPLEX_ENRICH,
				BatchStep.SCHOOL_SYNC, BatchStep.REDEVELOPMENT_SYNC, BatchStep.STATS_BUILD);
		// 실거래 키가 없어도 단지 보강과 통계는 독립적으로 돈다(보강 대상이 없으면 SUCCESS, 통계는 쌓인 거래로 계산).
		assertThat(savedRuns()).extracting(HouseBatchRun::getStatus)
			.containsExactly(BatchRunStatus.SKIPPED, BatchRunStatus.SKIPPED, BatchRunStatus.SUCCESS,
				BatchRunStatus.SKIPPED, BatchRunStatus.SKIPPED, BatchRunStatus.SUCCESS);
		verify(statLogic).rebuild(TODAY);
		assertThat(savedRuns()).allSatisfy(run -> assertThat(run.getApiCalls()).isZero());
		verify(unitLogic, never()).prepare(any());
		verify(tradeGateway, never()).fetchAll(any(), any(), any());
	}

	@Test
	void run_shouldPrepareThenCollectAndRecordTotals() {
		when(unitLogic.prepare(TODAY)).thenReturn(1000);
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		HouseBatchRun prepare = savedRun(BatchStep.COLLECT_UNIT_PREPARE);
		assertThat(prepare.getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(prepare.getDetail()).containsEntry("created", 1000);
		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(collect.getApiCalls()).isEqualTo(2);
		assertThat(collect.getRunDate()).isEqualTo(TODAY);
		assertThat(collect.getError()).isNull();
		assertThat(collect.getDetail()).containsEntry("unitsDue", 2).containsEntry("unitsDone", 2)
			.containsEntry("unitsFailed", 0).containsEntry("fetched", 20).containsEntry("inserted", 12)
			.containsEntry("updated", 2).containsEntry("unchanged", 6).containsEntry("cancelled", 4)
			.containsEntry("removed", 2).containsEntry("complexCreated", 2);
		assertThat(collect.getStartedTime()).isEqualTo(clock.millis());
	}

	@Test
	void run_shouldCollectUnitsInGivenPriorityOrder() {
		List<HouseCollectUnit> ordered = List.of(unit("11680", "202610"), unit("11110", "202609"),
			unit("11680", "202401"));
		when(unitLogic.findDueUnits(TODAY)).thenReturn(ordered);
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		InOrder inOrder = inOrder(tradeGateway);
		inOrder.verify(tradeGateway).fetchAll(eq("11680"), eq(YearMonth.of(2026, 10)), any());
		inOrder.verify(tradeGateway).fetchAll(eq("11110"), eq(YearMonth.of(2026, 9)), any());
		inOrder.verify(tradeGateway).fetchAll(eq("11680"), eq(YearMonth.of(2024, 1)), any());
	}

	@Test
	void run_shouldEndPartialAndLeaveRemainingUnitsUntouchedWhenBudgetRunsOut() {
		properties.getCallLimit().setTrade(2);
		when(unitLogic.findDueUnits(TODAY))
			.thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610"), unit("11140", "202610")));
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(collect.getApiCalls()).isEqualTo(2);
		assertThat(collect.getDetail()).containsEntry("stoppedBy", "BUDGET").containsEntry("unitsDone", 2);
		verify(tradeGateway, times(2)).fetchAll(any(), any(), any());
		verify(unitLogic, never()).markFailed(any());
	}

	@Test
	void run_shouldSubtractCallsAlreadyUsedToday() {
		properties.getCallLimit().setTrade(5);
		when(batchRunLogic.usedApiCalls(TODAY, BatchStep.TRADE_COLLECT)).thenReturn(4L);
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		verify(tradeGateway, times(1)).fetchAll(any(), any(), any());
		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(collect.getApiCalls()).isEqualTo(1);
	}

	@Test
	void run_shouldMarkUnitFailedAndContinueWhenFetchFails() {
		HouseCollectUnit bad = unit("11680", "202610");
		HouseCollectUnit good = unit("11110", "202610");
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(bad, good));
		gatewayReturns(TradeFetchResult.stopped(Outcome.FAILED, List.of(), 0), TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		verify(unitLogic).markFailed(bad.getId());
		verify(unitLogic, never()).markFailed(good.getId());
		verify(tradeLogic, times(1)).applyUnit(eq(good), any(), eq(TODAY));
		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(collect.getDetail()).containsEntry("unitsFailed", 1).containsEntry("unitsDone", 1);
	}

	@Test
	void run_shouldEndFailedWhenEveryUnitFails() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		gatewayReturns(TradeFetchResult.stopped(Outcome.FAILED, List.of(), 0));

		flow.run(TODAY);

		assertThat(savedRun(BatchStep.TRADE_COLLECT).getStatus()).isEqualTo(BatchRunStatus.FAILED);
		verify(unitLogic, times(2)).markFailed(any());
	}

	@Test
	void run_shouldMarkUnitFailedAndContinueWhenApplyThrows() {
		HouseCollectUnit first = unit("11680", "202610");
		HouseCollectUnit second = unit("11110", "202610");
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(first, second));
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));
		when(tradeLogic.applyUnit(eq(first), any(), any())).thenThrow(new IllegalStateException("db down"));

		flow.run(TODAY);

		verify(unitLogic).markFailed(first.getId());
		verify(tradeLogic).applyUnit(eq(second), any(), any());
		assertThat(savedRun(BatchStep.TRADE_COLLECT).getStatus()).isEqualTo(BatchRunStatus.PARTIAL);
	}

	@Test
	void run_shouldStopAndFailWhenKeyIsRejected() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		gatewayReturns(TradeFetchResult.stopped(Outcome.KEY_REJECTED, List.of(), 0));

		flow.run(TODAY);

		verify(tradeGateway, times(1)).fetchAll(any(), any(), any());
		verify(unitLogic, never()).markFailed(any());
		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(collect.getError()).isEqualTo("KEY_REJECTED");
		assertThat(collect.getApiCalls()).isEqualTo(1);
	}

	@Test
	void run_shouldStopWithPartialWhenQuotaExceededAndKeepUnitState() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		gatewayReturns(TradeFetchResult.stopped(Outcome.QUOTA_EXCEEDED, List.of(), 0));

		flow.run(TODAY);

		verify(tradeGateway, times(1)).fetchAll(any(), any(), any());
		verify(unitLogic, never()).markFailed(any());
		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(collect.getDetail()).containsEntry("stoppedBy", "QUOTA");
	}

	@Test
	void run_shouldGoOnToNextStepWhenAStepThrowsAndRecordOnlyExceptionClass() {
		when(unitLogic.prepare(TODAY)).thenThrow(new IllegalStateException("https://x?serviceKey=secret"));
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610")));
		gatewayReturns(TradeFetchResult.ok(List.of(), 0));

		flow.run(TODAY);

		HouseBatchRun prepare = savedRun(BatchStep.COLLECT_UNIT_PREPARE);
		assertThat(prepare.getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(prepare.getError()).isEqualTo("IllegalStateException").doesNotContain("secret");
		assertThat(savedRun(BatchStep.TRADE_COLLECT).getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
	}

	@Test
	void run_shouldKeepCallCountWhenCollectStepThrowsMidway() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of(unit("11680", "202610"), unit("11110", "202610")));
		when(tradeGateway.fetchAll(any(), any(), any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(2);
			budget.consume();
			if (budget.used() == 2) {
				throw new IllegalStateException("boom");
			}
			return TradeFetchResult.ok(List.of(), 0);
		});

		flow.run(TODAY);

		HouseBatchRun collect = savedRun(BatchStep.TRADE_COLLECT);
		assertThat(collect.getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(collect.getApiCalls()).isEqualTo(2);
		assertThat(collect.getDetail()).containsEntry("unitsDone", 1);
	}

	@Test
	void run_shouldNotFailWhenSavingTheRecordFails() {
		when(batchRunLogic.save(any())).thenThrow(new IllegalStateException("db down"));
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());

		assertThatCode(() -> flow.run(TODAY)).doesNotThrowAnyException();
		verify(unitLogic).findDueUnits(TODAY);
	}

	@Test
	void run_shouldRecordSixStepsInOrder() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());

		flow.run(TODAY);

		assertThat(savedRuns()).extracting(HouseBatchRun::getStep)
			.containsExactly(BatchStep.COLLECT_UNIT_PREPARE, BatchStep.TRADE_COLLECT, BatchStep.COMPLEX_ENRICH,
				BatchStep.SCHOOL_SYNC, BatchStep.REDEVELOPMENT_SYNC, BatchStep.STATS_BUILD);
	}

	@Test
	void run_shouldAlwaysSkipRedevelopmentSyncBecauseNoDatasetYet() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());

		flow.run(TODAY);

		HouseBatchRun redevelopment = savedRun(BatchStep.REDEVELOPMENT_SYNC);
		assertThat(redevelopment.getStatus()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(redevelopment.getApiCalls()).isZero();
		assertThat(redevelopment.getDetail()).containsEntry("reason", "DATASET_UNDECIDED");
	}

	@Test
	void run_shouldPassNewlyGeocodedCountFromComplexEnrichToSchoolSync() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());
		HouseComplex complex = HouseComplex.builder().aptSeq("A-1").lawdCd("11680").umdCd("10300").umdName("개포동")
			.bonbun("0652").bubun("0000").build();
		complex.setId("HOUSE_COMPLEX-0001");
		when(complexLogic.findGeocodeTargets(any(), anyInt())).thenReturn(List.of(complex));
		when(geocoder.geocode(any(), any())).thenReturn(GeocodeResult.found(37.5, 127.0, "서울특별시 강남구 개포로 307"));

		flow.run(TODAY);

		verify(schoolSyncStep).run(eq(TODAY), eq(1), any());
	}

	@Test
	void run_shouldRunSchoolSyncWithZeroNewlyGeocodedWhenNothingWasGeocoded() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());

		flow.run(TODAY);

		verify(schoolSyncStep).run(eq(TODAY), eq(0), any());
	}

	@Test
	void run_shouldRecordSchoolSyncFailedAndGoOnWhenTheStepThrows() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());
		when(schoolSyncStep.run(any(), anyInt(), any())).thenThrow(new IllegalStateException("https://x?serviceKey=secret"));

		flow.run(TODAY);

		HouseBatchRun school = savedRun(BatchStep.SCHOOL_SYNC);
		assertThat(school.getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(school.getError()).isEqualTo("IllegalStateException").doesNotContain("secret");
		assertThat(savedRun(BatchStep.REDEVELOPMENT_SYNC).getStatus()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(savedRun(BatchStep.STATS_BUILD).getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
	}

	@Test
	void run_shouldRecordStatsCountsInDetail() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());

		flow.run(TODAY);

		HouseBatchRun stats = savedRun(BatchStep.STATS_BUILD);
		assertThat(stats.getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(stats.getApiCalls()).isZero();
		assertThat(stats.getDetail()).containsEntry("candidates", 100).containsEntry("outliers", 3)
			.containsEntry("statRows", 40);
	}

	@Test
	void run_shouldRecordStatsFailedWhenRebuildThrowsAndKeepOtherRecords() {
		when(unitLogic.findDueUnits(TODAY)).thenReturn(List.of());
		when(statLogic.rebuild(any())).thenThrow(new IllegalStateException("db down"));

		flow.run(TODAY);

		HouseBatchRun stats = savedRun(BatchStep.STATS_BUILD);
		assertThat(stats.getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(stats.getError()).isEqualTo("IllegalStateException");
		assertThat(savedRuns()).hasSize(6);
	}

	@Test
	void run_shouldRecordStatsAfterTradeStepFailure() {
		when(unitLogic.findDueUnits(TODAY)).thenThrow(new IllegalStateException("db down"));

		flow.run(TODAY);

		assertThat(savedRun(BatchStep.STATS_BUILD).getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
	}

	@Test
	void run_shouldRecordComplexEnrichEvenWhenTradeStepFails() {
		when(unitLogic.findDueUnits(TODAY)).thenThrow(new IllegalStateException("db down"));

		flow.run(TODAY);

		assertThat(savedRun(BatchStep.TRADE_COLLECT).getStatus()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(savedRun(BatchStep.COMPLEX_ENRICH).getStatus()).isEqualTo(BatchRunStatus.SUCCESS);
	}
}
