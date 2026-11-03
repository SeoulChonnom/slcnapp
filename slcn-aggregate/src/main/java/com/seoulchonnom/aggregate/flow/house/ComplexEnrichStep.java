package com.seoulchonnom.aggregate.flow.house;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.BuildingRegisterGateway;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.ComplexGeocoder;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.RecapFailureTracker;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 3단계 단지 보강(COMPLEX_ENRICH)의 본문. 좌표(행안부)를 먼저, 건축물대장(공공데이터포털)을 그다음에 처리한다.
 * 두 부분은 서로 막지 않는다. 한쪽이 키가 없거나 멈춰도 다른 쪽은 계속한다.
 * 트랜잭션을 걸지 않는다. 외부 호출은 트랜잭션 밖이고, 결과 저장은 HouseComplexLogic이 단지 1건 단위로 한다.
 *
 * 단계 기록의 api_calls는 두 API의 합이고, API별 호출 수는 detail의 jusoCalls·buildingRegisterCalls에 남긴다.
 * 같은 날 다시 돌 때의 예산 차감이 API별로 맞아야 해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexEnrichStep {
	static final String JUSO_CALLS = "jusoCalls";
	static final String REGISTER_CALLS = "buildingRegisterCalls";
	/** 행안부 upstream 실패가 이만큼 연속되면 그날 좌표 처리를 접는다. 우리 쪽 속도 문제일 가능성이 크다. */
	static final int JUSO_UPSTREAM_STREAK_LIMIT = 3;
	/** 한 번에 읽는 대상 수 상한. 예산이 크더라도 메모리에 한꺼번에 올리지 않는다. */
	private static final int TARGET_BATCH_MAX = 5000;

	private final ComplexGeocoder complexGeocoder;
	private final BuildingRegisterGateway buildingRegisterGateway;
	private final HouseComplexLogic houseComplexLogic;
	private final HouseBatchRunLogic houseBatchRunLogic;
	private final HouseProperties houseProperties;

	public StepOutcome run(LocalDate today, StepProgress progress) {
		long jusoUsed = houseBatchRunLogic.usedDetailCalls(today, BatchStep.COMPLEX_ENRICH, JUSO_CALLS);
		long registerUsed = houseBatchRunLogic.usedDetailCalls(today, BatchStep.COMPLEX_ENRICH, REGISTER_CALLS);
		CallBudget jusoBudget = new CallBudget("juso", houseProperties.getCallLimit().getJuso(), jusoUsed);
		CallBudget registerBudget = new CallBudget("buildingRegister",
			houseProperties.getCallLimit().getBuildingRegister(), registerUsed);
		progress.put("jusoUsedBeforeRun", jusoUsed);
		progress.put("buildingRegisterUsedBeforeRun", registerUsed);
		for (String key : List.of("geocodeTargets", "geocodeDone", "geocodeNotFound", "geocodeUpstreamFailed",
			"geocodeDropped", "registerTargets", "registerDone", "registerNotFound", "registerFailed", "conflicts",
			"saveErrors")) {
			progress.put(key, 0);
		}

		Report report = new Report();
		try {
			geocode(today, jusoBudget, progress, report);
			register(today, registerBudget, progress, report);
		} finally {
			recordCalls(progress, jusoBudget, registerBudget);
		}
		return outcome(progress, report);
	}

	/** 한 단계에서 모은 중단·건너뜀 사유. */
	private static final class Report {
		final List<String> stops = new ArrayList<>();
		String geocodeSkipped;
		String registerSkipped;
		int processed;
	}

	private void geocode(LocalDate today, CallBudget budget, StepProgress progress, Report report) {
		int limit = (int)Math.max(1, Math.min(budget.remaining(), TARGET_BATCH_MAX));
		List<HouseComplex> targets = houseComplexLogic.findGeocodeTargets(today, limit);
		progress.put("geocodeTargets", targets.size());
		// 행안부 upstream 실패는 연속 3번이 되기 전까지 저장을 미룬다. 3번 연속이면 우리 쪽 속도 문제로 보고 버린다.
		List<HouseComplex> upstreamFailed = new ArrayList<>();
		for (HouseComplex complex : targets) {
			if (budget.remaining() <= 0) {
				stop(report, progress, "geocodeStoppedBy", "JUSO_BUDGET");
				break;
			}
			GeocodeResult result = complexGeocoder.geocode(complex, budget);
			if (result.outcome() == GeocodeResult.Outcome.UPSTREAM_FAILED) {
				upstreamFailed.add(complex);
				if (upstreamFailed.size() >= JUSO_UPSTREAM_STREAK_LIMIT) {
					progress.add("geocodeDropped", upstreamFailed.size());
					upstreamFailed.clear();
					stop(report, progress, "geocodeStoppedBy", "JUSO_UPSTREAM_STREAK");
					break;
				}
				continue;
			}
			// 연속이 끊겼으니 그전의 개별 upstream 실패는 시도 횟수에 반영한다.
			flushUpstreamFailures(upstreamFailed, progress);
			boolean halt = false;
			switch (result.outcome()) {
				case FOUND -> {
					if (applyGeocode(complex, result, progress)) {
						progress.add("geocodeDone", 1);
						report.processed++;
					}
				}
				case NOT_FOUND -> {
					if (applyGeocode(complex, result, progress)) {
						progress.add("geocodeNotFound", 1);
						report.processed++;
					}
				}
				case NOT_CONFIGURED -> {
					report.geocodeSkipped = "JUSO_KEY_MISSING";
					progress.put("geocodeSkipped", report.geocodeSkipped);
					halt = true;
				}
				case BUDGET_EXHAUSTED -> {
					stop(report, progress, "geocodeStoppedBy", "JUSO_BUDGET");
					halt = true;
				}
				case INTERRUPTED -> {
					stop(report, progress, "geocodeStoppedBy", "JUSO_INTERRUPTED");
					halt = true;
				}
				default -> {
				}
			}
			if (halt) {
				break;
			}
		}
		flushUpstreamFailures(upstreamFailed, progress);
	}

	/**
	 * 연속 3번에 이르지 않고 끊긴 upstream 실패는 설계대로 시도 횟수를 올린다.
	 */
	private void flushUpstreamFailures(List<HouseComplex> upstreamFailed, StepProgress progress) {
		for (HouseComplex complex : upstreamFailed) {
			if (applyGeocode(complex, GeocodeResult.of(GeocodeResult.Outcome.UPSTREAM_FAILED), progress)) {
				progress.add("geocodeUpstreamFailed", 1);
			}
		}
		upstreamFailed.clear();
	}

	private boolean applyGeocode(HouseComplex complex, GeocodeResult result, StepProgress progress) {
		try {
			houseComplexLogic.applyGeocode(complex, result);
			return true;
		} catch (ObjectOptimisticLockingFailureException e) {
			// 관리자가 같은 단지를 보정했다. 이 단지는 건너뛰고 다음 배치에서 보정된 값으로 다시 한다.
			progress.add("conflicts", 1);
			return false;
		} catch (RuntimeException e) {
			log.error("단지 좌표 저장 실패: complexId={}, error={}", complex.getId(), e.getClass().getSimpleName());
			progress.add("saveErrors", 1);
			return false;
		}
	}

	private void register(LocalDate today, CallBudget budget, StepProgress progress, Report report) {
		if (!buildingRegisterGateway.available()) {
			report.registerSkipped = "SERVICE_KEY_MISSING";
			progress.put("registerSkipped", report.registerSkipped);
			return;
		}
		int limit = (int)Math.max(1, Math.min(budget.remaining(), TARGET_BATCH_MAX));
		List<HouseComplex> targets = houseComplexLogic.findRegisterTargets(today, limit);
		progress.put("registerTargets", targets.size());
		RecapFailureTracker tracker = new RecapFailureTracker();
		for (HouseComplex complex : targets) {
			if (budget.remaining() <= 0) {
				stop(report, progress, "registerStoppedBy", "REGISTER_BUDGET");
				break;
			}
			RegisterLookupResult result = lookup(complex, budget, tracker);
			switch (result.outcome()) {
				case BUDGET_EXHAUSTED -> stop(report, progress, "registerStoppedBy", "REGISTER_BUDGET");
				case QUOTA_EXCEEDED -> stop(report, progress, "registerStoppedBy", "REGISTER_QUOTA");
				case KEY_REJECTED -> stop(report, progress, "registerStoppedBy", "REGISTER_KEY_REJECTED");
				default -> {
					if (applyRegister(complex, result, today, progress)) {
						switch (result.outcome()) {
							case FOUND -> progress.add("registerDone", 1);
							case NOT_FOUND -> progress.add("registerNotFound", 1);
							default -> progress.add("registerFailed", 1);
						}
						report.processed++;
					}
				}
			}
			if (progress.detail().containsKey("registerStoppedBy")) {
				break;
			}
		}
		progress.put("recapSkipped", tracker.shouldSkip());
	}

	/**
	 * 조회 키가 없는 단지는 호출 없이 결과 없음이다. 관리자가 지번을 보정했으면 그 값을 쓴다.
	 */
	private RegisterLookupResult lookup(HouseComplex complex, CallBudget budget, RecapFailureTracker tracker) {
		String bonbun = ComplexGeocoder.effectiveBonbun(complex);
		String bubun = ComplexGeocoder.effectiveBubun(complex);
		if (blank(complex.getUmdCd()) || blank(bonbun)) {
			return RegisterLookupResult.of(RegisterLookupResult.Outcome.NOT_FOUND);
		}
		return buildingRegisterGateway.find(complex.getLawdCd(), complex.getUmdCd(), bonbun,
			blank(bubun) ? "0000" : bubun, budget, tracker);
	}

	private boolean applyRegister(HouseComplex complex, RegisterLookupResult result, LocalDate today,
		StepProgress progress) {
		try {
			houseComplexLogic.applyRegister(complex, result, today);
			return true;
		} catch (ObjectOptimisticLockingFailureException e) {
			progress.add("conflicts", 1);
			return false;
		} catch (RuntimeException e) {
			log.error("단지 건축물대장 저장 실패: complexId={}, error={}", complex.getId(), e.getClass().getSimpleName());
			progress.add("saveErrors", 1);
			return false;
		}
	}

	private void stop(Report report, StepProgress progress, String detailKey, String reason) {
		report.stops.add(reason);
		progress.put(detailKey, reason);
	}

	/**
	 * 호출 수를 API별로 detail에, 합계를 api_calls에 남긴다. 예외가 나도 그때까지의 값이 기록된다.
	 */
	private void recordCalls(StepProgress progress, CallBudget jusoBudget, CallBudget registerBudget) {
		progress.put(JUSO_CALLS, jusoBudget.used());
		progress.put(REGISTER_CALLS, registerBudget.used());
		progress.apiCalls(jusoBudget.used() + registerBudget.used());
	}

	private StepOutcome outcome(StepProgress progress, Report report) {
		if (report.geocodeSkipped != null && report.registerSkipped != null) {
			return StepOutcome.of(BatchRunStatus.SKIPPED, "JUSO_KEY_MISSING,REGISTER_KEY_MISSING");
		}
		if (!report.stops.isEmpty()) {
			String error = String.join(",", report.stops);
			boolean keyRejectedOnly = report.stops.contains("REGISTER_KEY_REJECTED") && report.processed == 0;
			return StepOutcome.of(keyRejectedOnly ? BatchRunStatus.FAILED : BatchRunStatus.PARTIAL, error);
		}
		if (report.geocodeSkipped != null) {
			return StepOutcome.of(BatchRunStatus.PARTIAL, report.geocodeSkipped);
		}
		if (report.registerSkipped != null) {
			return StepOutcome.of(BatchRunStatus.PARTIAL, "REGISTER_" + report.registerSkipped);
		}
		int upstream = (Integer)progress.detail().get("geocodeUpstreamFailed")
			+ (Integer)progress.detail().get("registerFailed");
		if (upstream > 0) {
			return StepOutcome.of(BatchRunStatus.PARTIAL, "SOME_UPSTREAM_FAILED");
		}
		return StepOutcome.of(BatchRunStatus.SUCCESS);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
