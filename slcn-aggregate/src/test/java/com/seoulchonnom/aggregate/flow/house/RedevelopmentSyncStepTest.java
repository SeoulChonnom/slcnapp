package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.ComplexGeocoder;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult.Outcome;
import com.seoulchonnom.aggregate.house.logic.HouseRedevelopmentLogic;
import com.seoulchonnom.aggregate.house.logic.RedevelopmentLinkResult;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

class RedevelopmentSyncStepTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
	private static final GeocodeResult FOUND = GeocodeResult.found(37.5, 127.0, "서울특별시 종로구 종로1길 36");

	private final ComplexGeocoder geocoder = mock(ComplexGeocoder.class);
	private final HouseRedevelopmentLogic logic = mock(HouseRedevelopmentLogic.class);
	private final RedevelopmentSyncStep step = new RedevelopmentSyncStep(geocoder, logic);
	private StepProgress progress;

	@BeforeEach
	void setUp() {
		progress = new StepProgress();
		when(logic.count()).thenReturn(3L);
		when(logic.findGeocodeTargets()).thenReturn(List.of());
		when(logic.rebuildLinks()).thenReturn(new RedevelopmentLinkResult(3, 100, 7));
		when(logic.countByGeocodeStatus(any())).thenReturn(0L);
	}

	private static HouseRedevelopment target(String code) {
		HouseRedevelopment redevelopment = HouseRedevelopment.builder().sourceKey(code).name("구역" + code)
			.lawdCd("11110").jibunAddress("수송동146-12").roadAddress("종로1길36")
			.geocodeStatus(GeocodeStatus.PENDING).build();
		redevelopment.setId("id-" + code);
		return redevelopment;
	}

	private static CallBudget budget(int limit, long usedToday) {
		return new CallBudget("juso", limit, usedToday);
	}

	/** 호출마다 예산 n회를 쓰고 순서대로 결과를 돌려준다. 마지막 결과를 반복한다. */
	private void geocoderReturns(int callsEach, GeocodeResult... results) {
		List<GeocodeResult> queue = new ArrayList<>(List.of(results));
		when(geocoder.search(any(), any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(1);
			for (int i = 0; i < callsEach; i++) {
				budget.consume();
			}
			return queue.size() > 1 ? queue.remove(0) : queue.get(0);
		});
	}

	@Test
	void run_shouldSkipWithNoDataWhenThereAreNoRedevelopments() {
		when(logic.count()).thenReturn(0L);

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(progress.detail()).containsEntry("reason", "NO_DATA").containsEntry("jusoCalls", 0);
		verifyNoInteractions(geocoder);
		verify(logic, never()).rebuildLinks();
	}

	@Test
	void run_shouldSucceedAndRebuildLinksWhenNothingIsPending() {
		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
		verify(logic).rebuildLinks();
		verifyNoInteractions(geocoder);
		assertThat(progress.detail()).containsEntry("linkRows", 7).containsEntry("jusoCalls", 0)
			.containsEntry("geocodeFailed", 0L);
	}

	@Test
	void run_shouldGeocodeEveryPendingTargetAndSucceedWhenFailedOnesRemain() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2"), target("3")));
		geocoderReturns(2, FOUND, FOUND, GeocodeResult.of(Outcome.NOT_FOUND));
		when(logic.countByGeocodeStatus(GeocodeStatus.FAILED)).thenReturn(1L);

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
		verify(logic, times(3)).applyGeocode(any(), any());
		assertThat(progress.detail()).containsEntry("geocodeDone", 2).containsEntry("geocodeNotFound", 1)
			.containsEntry("geocodeFailed", 1L).containsEntry("jusoCalls", 6);
		assertThat(progress.apiCalls()).isEqualTo(6);
	}

	@Test
	void run_shouldRecordOnlyTheCallsThisStepUsedWhenBudgetIsSharedWithStageThree() {
		// 3단계가 이미 이 실행에서 4회를 썼다. 5단계의 jusoCalls는 자기 증가분(2)만이다.
		CallBudget shared = budget(4000, 100);
		for (int i = 0; i < 4; i++) {
			shared.consume();
		}
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1")));
		geocoderReturns(2, FOUND);

		step.run(TODAY, shared, progress);

		assertThat(progress.detail()).containsEntry("jusoCalls", 2).containsEntry("jusoUsedBeforeRun", 104L);
		assertThat(progress.apiCalls()).isEqualTo(2);
		assertThat(shared.used()).isEqualTo(6);
	}

	@Test
	void run_shouldEndPartialAndStopWhenSharedBudgetRunsOut() {
		// 상한 5, 오늘 3단계가 3번 썼다. 남은 2회로 한 구역(2회)만 처리하고 멈춘다.
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2")));
		geocoderReturns(2, FOUND);
		when(logic.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(1L);

		StepOutcome outcome = step.run(TODAY, budget(5, 3), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("JUSO_BUDGET");
		verify(geocoder, times(1)).search(any(), any());
		verify(logic).rebuildLinks();
		assertThat(progress.detail()).containsEntry("geocodeStoppedBy", "JUSO_BUDGET");
	}

	@Test
	void run_shouldNotCallGeocoderWhenBudgetIsAlreadySpentByStageThree() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1")));
		when(logic.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(1L);

		StepOutcome outcome = step.run(TODAY, budget(10, 10), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		verifyNoInteractions(geocoder);
		verify(logic).rebuildLinks();
	}

	@Test
	void run_shouldStopWhenJusoKeyIsMissing() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2")));
		geocoderReturns(0, GeocodeResult.of(Outcome.NOT_CONFIGURED));
		when(logic.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(2L);

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("JUSO_KEY_MISSING");
		verify(geocoder, times(1)).search(any(), any());
	}

	@Test
	void run_shouldDropThreeConsecutiveUpstreamFailuresWithoutCountingAttempts() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2"), target("3"), target("4")));
		geocoderReturns(1, GeocodeResult.of(Outcome.UPSTREAM_FAILED));
		when(logic.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(4L);

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("JUSO_UPSTREAM_STREAK");
		verify(geocoder, times(3)).search(any(), any());
		verify(logic, never()).applyGeocode(any(), any());
		assertThat(progress.detail()).containsEntry("geocodeDropped", 3);
	}

	@Test
	void run_shouldCountIsolatedUpstreamFailureAsAnAttempt() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2")));
		geocoderReturns(1, GeocodeResult.of(Outcome.UPSTREAM_FAILED), FOUND);
		when(logic.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(1L);

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.PARTIAL);
		assertThat(outcome.error()).isEqualTo("SOME_UPSTREAM_FAILED");
		verify(logic, times(2)).applyGeocode(any(), any());
		assertThat(progress.detail()).containsEntry("geocodeUpstreamFailed", 1).containsEntry("geocodeDone", 1);
	}

	@Test
	void run_shouldSkipConflictedRowAndGoOn() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1"), target("2")));
		geocoderReturns(1, FOUND);
		when(logic.applyGeocode(argThat(r -> "1".equals(r.getSourceKey())), any()))
			.thenThrow(new ObjectOptimisticLockingFailureException(HouseRedevelopment.class, "id-1"));

		StepOutcome outcome = step.run(TODAY, budget(4000, 0), progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(progress.detail()).containsEntry("conflicts", 1).containsEntry("geocodeDone", 1);
	}

	@Test
	void run_shouldRecordCallsEvenWhenLinkRebuildThrows() {
		when(logic.findGeocodeTargets()).thenReturn(List.of(target("1")));
		geocoderReturns(2, FOUND);
		when(logic.rebuildLinks()).thenThrow(new IllegalStateException("db down"));

		assertThatThrownBy(() -> step.run(TODAY, budget(4000, 0), progress)).isInstanceOf(IllegalStateException.class);

		assertThat(progress.detail()).containsEntry("jusoCalls", 2);
		assertThat(progress.apiCalls()).isEqualTo(2);
	}

	@Test
	void keywords_shouldTryGeocodeQueryThenRoadThenJibun() {
		HouseRedevelopment redevelopment = target("1");
		redevelopment.setGeocodeQuery("서울특별시 종로구 종로 1");

		assertThat(RedevelopmentSyncStep.keywords(redevelopment)).containsExactly("서울특별시 종로구 종로 1",
			"서울특별시 종로구 종로1길36", "서울특별시 종로구 수송동146-12");
	}

	@Test
	void keywords_shouldSkipMissingValues() {
		HouseRedevelopment redevelopment = target("1");
		redevelopment.setRoadAddress(null);

		assertThat(RedevelopmentSyncStep.keywords(redevelopment)).containsExactly("서울특별시 종로구 수송동146-12");
	}
}
