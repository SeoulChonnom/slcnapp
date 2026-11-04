package com.seoulchonnom.aggregate.flow.house;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.SchoolFetchResult;
import com.seoulchonnom.aggregate.house.collect.SchoolGateway;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseReferenceLogic;
import com.seoulchonnom.aggregate.house.logic.SchoolSyncResult;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;

/**
 * 4단계 학교 동기화(SCHOOL_SYNC)의 본문. 마지막 SUCCESS가 없거나 180일 이상 지났을 때만 학교를 새로 받는다.
 * 외부 호출은 트랜잭션 밖이고, 받은 뒤의 교체와 최근접 재생성은 HouseReferenceLogic이 한 트랜잭션으로 한다.
 *
 * 갱신 시점 전이라도 3단계에서 좌표가 새로 확정된 단지가 있으면 수집 없이 최근접만 다시 계산한다.
 * 이 기록은 SUCCESS가 아니라 detail.recomputeOnly = true인 SKIPPED다. SUCCESS로 남기면 180일 주기가 흐트러진다.
 */
@Component
@RequiredArgsConstructor
public class SchoolSyncStep {
	/** 학교 목록은 자주 바뀌지 않아 이 주기로만 받는다. */
	static final int REFRESH_DAYS = 180;

	private final SchoolGateway schoolGateway;
	private final HouseReferenceLogic houseReferenceLogic;
	private final HouseBatchRunLogic houseBatchRunLogic;
	private final HouseProperties houseProperties;

	/**
	 * @param newlyGeocoded 이번 실행의 3단계에서 좌표가 새로 DONE이 된 단지 수
	 */
	public StepOutcome run(LocalDate today, int newlyGeocoded, StepProgress progress) {
		Optional<LocalDate> lastSuccess = houseBatchRunLogic.findLastSuccessRunDate(BatchStep.SCHOOL_SYNC);
		progress.put("lastSuccessDate", lastSuccess.map(LocalDate::toString).orElse(null));
		boolean due = lastSuccess.isEmpty() || ChronoUnit.DAYS.between(lastSuccess.get(), today) >= REFRESH_DAYS;
		if (!due) {
			if (newlyGeocoded > 0) {
				SchoolSyncResult result = houseReferenceLogic.recomputeNearest();
				progress.put("recomputeOnly", true);
				progress.put("newlyGeocoded", newlyGeocoded);
				progress.put("schools", result.schools());
				progress.put("complexes", result.complexes());
				progress.put("nearestRows", result.nearestRows());
				return StepOutcome.of(BatchRunStatus.SKIPPED, "RECOMPUTE_ONLY");
			}
			progress.put("reason", "NOT_DUE");
			return StepOutcome.of(BatchRunStatus.SKIPPED);
		}
		if (!schoolGateway.available()) {
			progress.put("reason", "SERVICE_KEY_MISSING");
			return StepOutcome.of(BatchRunStatus.SKIPPED);
		}

		long usedToday = houseBatchRunLogic.usedApiCalls(today, BatchStep.SCHOOL_SYNC);
		CallBudget budget = new CallBudget("school", houseProperties.getCallLimit().getSchool(), usedToday);
		progress.put("usedBeforeRun", usedToday);
		SchoolFetchResult fetched;
		try {
			fetched = schoolGateway.fetchAll(budget);
		} finally {
			progress.apiCalls(budget.used());
		}
		progress.put("skippedRows", fetched.skippedRows());
		if (fetched.outcome() != SchoolFetchResult.Outcome.OK) {
			// 일부 페이지만 받았을 수 있다. 기존 학교를 그대로 두고 다음 날 다시 한다.
			return StepOutcome.of(BatchRunStatus.FAILED, fetched.outcome().name());
		}
		progress.put("fetched", fetched.records().size());
		if (fetched.records().isEmpty()) {
			return StepOutcome.of(BatchRunStatus.FAILED, "NO_SCHOOLS");
		}

		SchoolSyncResult result = houseReferenceLogic.replaceSchools(fetched.records(), today);
		progress.put("schools", result.schools());
		progress.put("deleted", result.deleted());
		progress.put("complexes", result.complexes());
		progress.put("nearestRows", result.nearestRows());
		return StepOutcome.of(BatchRunStatus.SUCCESS);
	}
}
