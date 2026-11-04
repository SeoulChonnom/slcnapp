package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.BuildingRegisterGateway;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.ComplexGeocoder;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult.Outcome;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.collect.RegisterSummary;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

class ComplexEnrichStepTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

	private final ComplexGeocoder geocoder = mock(ComplexGeocoder.class);
	private final BuildingRegisterGateway registerGateway = mock(BuildingRegisterGateway.class);
	private final HouseComplexLogic complexLogic = mock(HouseComplexLogic.class);
	private final HouseBatchRunLogic batchRunLogic = mock(HouseBatchRunLogic.class);
	private final HouseProperties properties = new HouseProperties();
	private ComplexEnrichStep step;
	private StepProgress progress;

	@BeforeEach
	void setUp() {
		step = new ComplexEnrichStep(geocoder, registerGateway, complexLogic, batchRunLogic, properties);
		progress = new StepProgress();
		when(registerGateway.available()).thenReturn(true);
		when(batchRunLogic.usedDetailCalls(any(), any(), any())).thenReturn(0L);
	}

	/** 5단계와 나눠 쓰는 행안부 예산. 오늘 이미 쓴 호출 수를 빼고 시작한다. */
	private CallBudget jusoBudget(long usedToday) {
		return new CallBudget("juso", properties.getCallLimit().getJuso(), usedToday);
	}

	private HouseComplex complex(String id) {
		HouseComplex complex = HouseComplex.builder().aptSeq("A-" + id).lawdCd("11680").umdCd("10300")
			.umdName("개포동").bonbun("0652").bubun("0000").build();
		complex.setId(id);
		return complex;
	}

	/** 호출마다 행안부 예산 n회를 쓰고 순서대로 결과를 돌려준다. 마지막 결과를 반복한다. */
	private void geocoderReturns(int callsEach, GeocodeResult... results) {
		List<GeocodeResult> queue = new ArrayList<>(List.of(results));
		when(geocoder.geocode(any(), any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(1);
			for (int i = 0; i < callsEach; i++) {
				budget.consume();
			}
			return queue.size() > 1 ? queue.remove(0) : queue.get(0);
		});
	}

	private void registerReturns(RegisterLookupResult... results) {
		List<RegisterLookupResult> queue = new ArrayList<>(List.of(results));
		when(registerGateway.find(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(4);
			budget.consume();
			return queue.size() > 1 ? queue.remove(0) : queue.get(0);
		});
	}

	private static final GeocodeResult FOUND = GeocodeResult.found(37.5, 127.0, "서울특별시 강남구 개포로 307");
	private static final GeocodeResult NOT_FOUND = GeocodeResult.of(Outcome.NOT_FOUND);
	private static final GeocodeResult UPSTREAM = GeocodeResult.of(Outcome.UPSTREAM_FAILED);
	private static final RegisterLookupResult REGISTER_FOUND = RegisterLookupResult
		.found(new RegisterSummary(405, null, null, null, null));

	private List<GeocodeResult.Outcome> appliedGeocodeOutcomes() {
		ArgumentCaptor<GeocodeResult> captor = ArgumentCaptor.forClass(GeocodeResult.class);
		verify(complexLogic, atLeast(0)).applyGeocode(any(), captor.capture());
		return captor.getAllValues().stream().map(GeocodeResult::outcome).toList();
	}

	@Test
	void run_shouldApplyGeocodeAndRegisterAndRecordApiSpecificCalls() {
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1"), complex("c2")));
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1")));
		geocoderReturns(2, FOUND, NOT_FOUND);
		registerReturns(REGISTER_FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(appliedGeocodeOutcomes()).containsExactly(Outcome.FOUND, Outcome.NOT_FOUND);
		verify(complexLogic).applyRegister(any(), eq(REGISTER_FOUND), eq(TODAY));
		assertThat(progress.detail()).containsEntry("jusoCalls", 4).containsEntry("buildingRegisterCalls", 1)
			.containsEntry("geocodeDone", 1).containsEntry("geocodeNotFound", 1).containsEntry("registerDone", 1);
		assertThat(progress.apiCalls()).isEqualTo(5);
	}

	@Test
	void run_shouldStopGeocodingAfterThreeConsecutiveUpstreamFailuresWithoutRaisingAttempts() {
		List<HouseComplex> targets = List.of(complex("c1"), complex("c2"), complex("c3"), complex("c4"));
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(targets);
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c9")));
		geocoderReturns(1, UPSTREAM);
		registerReturns(REGISTER_FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).contains("JUSO_UPSTREAM_STREAK");
		verify(geocoder, times(3)).geocode(any(), any());
		// 연속 3번이면 그 단지들의 attempts는 올리지 않는다. 네 번째 단지는 시도조차 하지 않는다.
		verify(complexLogic, never()).applyGeocode(any(), any());
		assertThat(progress.detail()).containsEntry("geocodeDropped", 3).containsEntry("geocodeStoppedBy",
			"JUSO_UPSTREAM_STREAK");
		// 건축물대장은 계속한다.
		verify(complexLogic).applyRegister(any(), any(), eq(TODAY));
	}

	@Test
	void run_shouldRaiseAttemptsForIsolatedUpstreamFailuresBeforeStreakReachesThree() {
		HouseComplex c1 = complex("c1");
		HouseComplex c2 = complex("c2");
		HouseComplex c3 = complex("c3");
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(c1, c2, c3));
		geocoderReturns(1, UPSTREAM, UPSTREAM, FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		// 두 번 실패하고 세 번째가 성공했으니 앞의 두 실패는 설계대로 attempts+1 대상이다.
		InOrderApplied applied = new InOrderApplied();
		assertThat(applied.outcomesFor(c1)).containsExactly(Outcome.UPSTREAM_FAILED);
		assertThat(applied.outcomesFor(c2)).containsExactly(Outcome.UPSTREAM_FAILED);
		assertThat(applied.outcomesFor(c3)).containsExactly(Outcome.FOUND);
		assertThat(progress.detail()).containsEntry("geocodeUpstreamFailed", 2).containsEntry("geocodeDone", 1);
		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("SOME_UPSTREAM_FAILED");
	}

	/** applyGeocode 호출을 단지별로 모아 본다. */
	private final class InOrderApplied {
		List<Outcome> outcomesFor(HouseComplex complex) {
			ArgumentCaptor<GeocodeResult> captor = ArgumentCaptor.forClass(GeocodeResult.class);
			verify(complexLogic, atLeast(0)).applyGeocode(eq(complex), captor.capture());
			return captor.getAllValues().stream().map(GeocodeResult::outcome).toList();
		}
	}

	@Test
	void run_shouldRaiseAttemptsForPendingUpstreamFailuresWhenTargetsEndBeforeStreakReachesThree() {
		HouseComplex c1 = complex("c1");
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(c1));
		geocoderReturns(1, UPSTREAM);

		step.run(TODAY, jusoBudget(0), progress);

		assertThat(new InOrderApplied().outcomesFor(c1)).containsExactly(Outcome.UPSTREAM_FAILED);
	}

	@Test
	void run_shouldResetStreakWhenAnyNonUpstreamOutcomeInBetween() {
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(
			List.of(complex("c1"), complex("c2"), complex("c3"), complex("c4"), complex("c5")));
		geocoderReturns(1, UPSTREAM, UPSTREAM, NOT_FOUND, UPSTREAM, UPSTREAM);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		verify(geocoder, times(5)).geocode(any(), any());
		assertThat(progress.detail()).doesNotContainKey("geocodeStoppedBy").containsEntry("geocodeDropped", 0);
		assertThat(outcome.error()).isEqualTo("SOME_UPSTREAM_FAILED");
	}

	@Test
	void run_shouldSkipGeocodeOnlyWhenJusoKeyMissingAndStillRunRegister() {
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1"), complex("c2")));
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1")));
		geocoderReturns(1, GeocodeResult.of(Outcome.NOT_CONFIGURED));
		registerReturns(REGISTER_FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		verify(geocoder, times(1)).geocode(any(), any());
		verify(complexLogic, never()).applyGeocode(any(), any());
		verify(complexLogic).applyRegister(any(), any(), any());
		assertThat(progress.detail()).containsEntry("geocodeSkipped", "JUSO_KEY_MISSING");
		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("JUSO_KEY_MISSING");
	}

	@Test
	void run_shouldEndSkippedWhenBothKeysMissing() {
		when(registerGateway.available()).thenReturn(false);
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1")));
		geocoderReturns(1, GeocodeResult.of(Outcome.NOT_CONFIGURED));

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SKIPPED);
		verify(registerGateway, never()).find(any(), any(), any(), any(), any(), any());
		assertThat(progress.detail()).containsEntry("registerSkipped", "SERVICE_KEY_MISSING");
	}

	@Test
	void run_shouldRunGeocodeAndSkipRegisterWhenDataGoKrKeyMissing() {
		when(registerGateway.available()).thenReturn(false);
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1")));
		geocoderReturns(1, FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("REGISTER_SERVICE_KEY_MISSING");
		verify(complexLogic).applyGeocode(any(), eq(FOUND));
	}

	@Test
	void run_shouldEndPartialWhenJusoBudgetRunsOutAndSubtractUsageOfJusoOnly() {
		properties.getCallLimit().setJuso(10);
		properties.getCallLimit().setBuildingRegister(5000);
		// 오늘 행안부를 8번 썼고 건축물대장은 4,000번 썼다. 행안부 예산은 2회, 건축물대장은 1,000회가 남는다.
		when(batchRunLogic.usedDetailCalls(TODAY, BatchStep.COMPLEX_ENRICH, "buildingRegisterCalls"))
			.thenReturn(4000L);
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt()))
			.thenReturn(List.of(complex("c1"), complex("c2"), complex("c3")));
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("r1")));
		geocoderReturns(1, FOUND);
		registerReturns(REGISTER_FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(8), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("JUSO_BUDGET");
		verify(geocoder, times(2)).geocode(any(), any());
		verify(complexLogic).findGeocodeTargets(TODAY, 2);
		verify(complexLogic).findRegisterTargets(TODAY, 1000);
		assertThat(progress.detail()).containsEntry("jusoUsedBeforeRun", 8L)
			.containsEntry("buildingRegisterUsedBeforeRun", 4000L).containsEntry("jusoCalls", 2)
			.containsEntry("buildingRegisterCalls", 1);
		assertThat(progress.apiCalls()).isEqualTo(3);
	}

	@Test
	void run_shouldEndPartialWhenRegisterBudgetRunsOut() {
		properties.getCallLimit().setBuildingRegister(2);
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt()))
			.thenReturn(List.of(complex("r1"), complex("r2"), complex("r3")));
		registerReturns(REGISTER_FOUND);

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("REGISTER_BUDGET");
		verify(registerGateway, times(2)).find(any(), any(), any(), any(), any(), any());
		verify(complexLogic, times(2)).applyRegister(any(), any(), any());
	}

	@Test
	void run_shouldStopWithPartialWhenGatewayReportsBudgetExhaustedMidLookup() {
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("r1"), complex("r2")));
		when(registerGateway.find(any(), any(), any(), any(), any(), any()))
			.thenReturn(RegisterLookupResult.of(RegisterLookupResult.Outcome.BUDGET_EXHAUSTED));

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		verify(registerGateway, times(1)).find(any(), any(), any(), any(), any(), any());
		verify(complexLogic, never()).applyRegister(any(), any(), any());
	}

	@Test
	void run_shouldFailWhenRegisterKeyRejectedAndNothingProcessed() {
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("r1"), complex("r2")));
		registerReturns(RegisterLookupResult.of(RegisterLookupResult.Outcome.KEY_REJECTED));

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(outcome.error()).isEqualTo("REGISTER_KEY_REJECTED");
		verify(registerGateway, times(1)).find(any(), any(), any(), any(), any(), any());
	}

	@Test
	void run_shouldApplyNotFoundWithoutCallingGatewayWhenLotNumberMissing() {
		HouseComplex noLot = complex("r1");
		noLot.setBonbun(null);
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(noLot));

		step.run(TODAY, jusoBudget(0), progress);

		verify(registerGateway, never()).find(any(), any(), any(), any(), any(), any());
		ArgumentCaptor<RegisterLookupResult> captor = ArgumentCaptor.forClass(RegisterLookupResult.class);
		verify(complexLogic).applyRegister(eq(noLot), captor.capture(), eq(TODAY));
		assertThat(captor.getValue().outcome()).isEqualTo(RegisterLookupResult.Outcome.NOT_FOUND);
	}

	@Test
	void run_shouldLookUpWithOverriddenLotNumber() {
		HouseComplex overridden = complex("r1");
		overridden.setAddressOverride(new AddressOverride(null, "0123", "0004"));
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(overridden));
		registerReturns(REGISTER_FOUND);

		step.run(TODAY, jusoBudget(0), progress);

		verify(registerGateway).find(eq("11680"), eq("10300"), eq("0123"), eq("0004"), any(), any());
	}

	@Test
	void run_shouldSkipComplexOnVersionConflictAndContinue() {
		HouseComplex conflicted = complex("c1");
		HouseComplex next = complex("c2");
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(conflicted, next));
		geocoderReturns(1, FOUND);
		when(complexLogic.applyGeocode(eq(conflicted), any()))
			.thenThrow(new ObjectOptimisticLockingFailureException(HouseComplex.class, "c1"));

		StepOutcome outcome = step.run(TODAY, jusoBudget(0), progress);

		verify(complexLogic).applyGeocode(eq(next), any());
		assertThat(progress.detail()).containsEntry("conflicts", 1).containsEntry("geocodeDone", 1);
		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
	}

	@Test
	void run_shouldRecordCallsEvenWhenStepThrows() {
		when(complexLogic.findGeocodeTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("c1")));
		when(geocoder.geocode(any(), any())).thenAnswer(invocation -> {
			((CallBudget)invocation.getArgument(1)).consume();
			throw new IllegalStateException("boom");
		});

		assertThatThrownBy(() -> step.run(TODAY, jusoBudget(0), progress)).isInstanceOf(IllegalStateException.class);

		assertThat(progress.detail()).containsEntry("jusoCalls", 1);
		assertThat(progress.apiCalls()).isEqualTo(1);
	}

	@Test
	void run_shouldRecordRecapSkippedFlag() {
		when(complexLogic.findRegisterTargets(eq(TODAY), anyInt())).thenReturn(List.of(complex("r1")));
		registerReturns(REGISTER_FOUND);

		step.run(TODAY, jusoBudget(0), progress);

		assertThat(progress.detail()).containsEntry("recapSkipped", false).containsEntry("registerTargets", 1);
	}
}
