package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult;
import com.seoulchonnom.aggregate.house.collect.SchoolGateway;
import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseReferenceLogic;
import com.seoulchonnom.aggregate.house.logic.SchoolSyncResult;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

class SchoolSyncStepTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
	private static final SchoolRecord SCHOOL = new SchoolRecord("B1", "서울초등학교", SchoolLevel.E, 37.5, 127.0);

	private final SchoolGateway gateway = mock(SchoolGateway.class);
	private final HouseReferenceLogic referenceLogic = mock(HouseReferenceLogic.class);
	private final HouseBatchRunLogic batchRunLogic = mock(HouseBatchRunLogic.class);
	private final HouseProperties properties = new HouseProperties();
	private SchoolSyncStep step;
	private StepProgress progress;

	@BeforeEach
	void setUp() {
		step = new SchoolSyncStep(gateway, referenceLogic, batchRunLogic, properties);
		progress = new StepProgress();
		when(gateway.available()).thenReturn(true);
		when(batchRunLogic.findLastSuccessRunDate(BatchStep.SCHOOL_SYNC)).thenReturn(Optional.empty());
		when(batchRunLogic.usedApiCalls(any(), any())).thenReturn(0L);
		when(referenceLogic.replaceSchools(any(), any())).thenReturn(new SchoolSyncResult(1313, 2, 3000, 8800));
		when(referenceLogic.recomputeNearest()).thenReturn(new SchoolSyncResult(1313, 0, 3010, 8830));
	}

	private void fetchReturns(SchoolFetchResult result, int calls) {
		when(gateway.fetchAll(any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(0);
			for (int i = 0; i < calls; i++) {
				budget.consume();
			}
			return result;
		});
	}

	@Test
	void run_shouldCollectAndReplaceWhenNeverSucceeded() {
		fetchReturns(SchoolFetchResult.ok(List.of(SCHOOL), 3), 2);

		StepOutcome outcome = step.run(TODAY, 0, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SUCCESS);
		assertThat(progress.apiCalls()).isEqualTo(2);
		assertThat(progress.detail()).containsEntry("fetched", 1).containsEntry("skippedRows", 3)
			.containsEntry("schools", 1313).containsEntry("deleted", 2).containsEntry("nearestRows", 8800);
		verify(referenceLogic).replaceSchools(List.of(SCHOOL), TODAY);
	}

	@Test
	void run_shouldCollectWhenLastSuccessIs180DaysAgo() {
		when(batchRunLogic.findLastSuccessRunDate(BatchStep.SCHOOL_SYNC)).thenReturn(Optional.of(TODAY.minusDays(180)));
		fetchReturns(SchoolFetchResult.ok(List.of(SCHOOL), 0), 2);

		assertThat(step.run(TODAY, 0, progress).status()).isEqualTo(BatchRunStatus.SUCCESS);
		verify(gateway).fetchAll(any());
	}

	@Test
	void run_shouldSkipWithoutCollectingWhenLastSuccessIs179DaysAgo() {
		when(batchRunLogic.findLastSuccessRunDate(BatchStep.SCHOOL_SYNC)).thenReturn(Optional.of(TODAY.minusDays(179)));

		StepOutcome outcome = step.run(TODAY, 0, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(progress.detail()).containsEntry("reason", "NOT_DUE").doesNotContainKey("recomputeOnly");
		verify(gateway, never()).fetchAll(any());
		verify(referenceLogic, never()).recomputeNearest();
	}

	@Test
	void run_shouldRecomputeNearestOnlyAndRecordSkippedWhenNewlyGeocodedAndNotDue() {
		when(batchRunLogic.findLastSuccessRunDate(BatchStep.SCHOOL_SYNC)).thenReturn(Optional.of(TODAY.minusDays(30)));

		StepOutcome outcome = step.run(TODAY, 5, progress);

		// SUCCESS로 남기면 마지막 SUCCESS 날짜가 바뀌어 180일 주기가 밀린다.
		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(progress.detail()).containsEntry("recomputeOnly", true).containsEntry("newlyGeocoded", 5)
			.containsEntry("complexes", 3010).containsEntry("nearestRows", 8830);
		assertThat(progress.apiCalls()).isZero();
		verify(referenceLogic).recomputeNearest();
		verify(gateway, never()).fetchAll(any());
		verify(referenceLogic, never()).replaceSchools(any(), any());
	}

	@Test
	void run_shouldSkipWhenDueButServiceKeyMissing() {
		when(gateway.available()).thenReturn(false);

		StepOutcome outcome = step.run(TODAY, 3, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.SKIPPED);
		assertThat(progress.detail()).containsEntry("reason", "SERVICE_KEY_MISSING");
		verify(gateway, never()).fetchAll(any());
	}

	@Test
	void run_shouldFailAndKeepExistingSchoolsWhenAnyPageFails() {
		fetchReturns(SchoolFetchResult.stopped(SchoolFetchResult.Outcome.FAILED, 0), 5);

		StepOutcome outcome = step.run(TODAY, 0, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(outcome.error()).isEqualTo("FAILED");
		assertThat(progress.apiCalls()).isEqualTo(5);
		verify(referenceLogic, never()).replaceSchools(any(), any());
	}

	@Test
	void run_shouldFailWithoutReplacingWhenNoSchoolsCame() {
		fetchReturns(SchoolFetchResult.ok(List.of(), 10), 2);

		StepOutcome outcome = step.run(TODAY, 0, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(outcome.error()).isEqualTo("NO_SCHOOLS");
		verify(referenceLogic, never()).replaceSchools(any(), any());
	}

	@Test
	void run_shouldSubtractCallsAlreadyUsedTodayFromSchoolBudget() {
		properties.getCallLimit().setSchool(3);
		when(batchRunLogic.usedApiCalls(TODAY, BatchStep.SCHOOL_SYNC)).thenReturn(2L);
		when(gateway.fetchAll(any())).thenAnswer(invocation -> {
			CallBudget budget = invocation.getArgument(0);
			assertThat(budget.remaining()).isEqualTo(1);
			return SchoolFetchResult.stopped(SchoolFetchResult.Outcome.BUDGET_EXHAUSTED, 0);
		});

		StepOutcome outcome = step.run(TODAY, 0, progress);

		assertThat(outcome.status()).isEqualTo(BatchRunStatus.FAILED);
		assertThat(outcome.error()).isEqualTo("BUDGET_EXHAUSTED");
		assertThat(progress.detail()).containsEntry("usedBeforeRun", 2L);
	}
}
