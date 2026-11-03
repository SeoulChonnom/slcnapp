package com.seoulchonnom.aggregate.flow.house;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.config.HouseProperties;
import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.CollectUnitPriority;
import com.seoulchonnom.aggregate.house.collect.TradeFetchResult;
import com.seoulchonnom.aggregate.house.collect.TradeGateway;
import com.seoulchonnom.aggregate.house.logic.HouseBatchRunLogic;
import com.seoulchonnom.aggregate.house.logic.HouseCollectUnitLogic;
import com.seoulchonnom.aggregate.house.logic.HouseStatLogic;
import com.seoulchonnom.aggregate.house.logic.HouseTradeLogic;
import com.seoulchonnom.aggregate.house.logic.StatRebuildResult;
import com.seoulchonnom.aggregate.house.logic.TradeApplyResult;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내집찾기 야간 배치 오케스트레이션. 트랜잭션을 걸지 않는다. 외부 호출 중에 커넥션·락을 잡지 않으려는 것이고,
 * 저장은 Logic의 짧은 트랜잭션 메서드(수집 단위 1개 = 트랜잭션 1개)가 맡는다.
 * 단계마다 house_batch_run에 한 행을 남기고, 한 단계가 실패해도 다음 단계로 넘어간다.
 *
 * 지금은 1(수집 단위 준비)·2(실거래 수집)·3(단지 보강)·6(통계)단계가 있다. 4(학교)·5(정비사업)단계는 다음 PR에서 3단계와 6단계 사이에 끼운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseNightlyFlow {
	private final TradeGateway tradeGateway;
	private final HouseCollectUnitLogic houseCollectUnitLogic;
	private final HouseTradeLogic houseTradeLogic;
	private final HouseBatchRunLogic houseBatchRunLogic;
	private final BatchStepRecorder batchStepRecorder;
	private final HouseProperties houseProperties;
	private final ComplexEnrichStep complexEnrichStep;
	private final HouseStatLogic houseStatLogic;

	public void run(LocalDate today) {
		log.info("내집찾기 야간 배치 시작: runDate={}", today);
		batchStepRecorder.record(today, BatchStep.COLLECT_UNIT_PREPARE, progress -> prepareUnits(today, progress));
		batchStepRecorder.record(today, BatchStep.TRADE_COLLECT, progress -> collectTrades(today, progress));
		batchStepRecorder.record(today, BatchStep.COMPLEX_ENRICH, progress -> complexEnrichStep.run(today, progress));
		batchStepRecorder.record(today, BatchStep.STATS_BUILD, progress -> buildStats(today, progress));
		log.info("내집찾기 야간 배치 끝: runDate={}", today);
	}

	/**
	 * 통계는 수집 키와 무관하다. 이미 쌓인 거래로 계산하므로 키가 없어도 실행한다. 한 트랜잭션이라 실패하면 이전 통계가 남는다.
	 */
	private StepOutcome buildStats(LocalDate today, StepProgress progress) {
		StatRebuildResult result = houseStatLogic.rebuild(today);
		progress.put("candidates", result.candidates());
		progress.put("outliers", result.outliers());
		progress.put("statRows", result.statRows());
		return StepOutcome.of(BatchRunStatus.SUCCESS);
	}

	/**
	 * 키가 없으면 수집 단위도 만들지 않는다. 수집할 수 없는 단위를 쌓아 둘 이유가 없다.
	 */
	private StepOutcome prepareUnits(LocalDate today, StepProgress progress) {
		if (!tradeGateway.available()) {
			progress.put("reason", "SERVICE_KEY_MISSING");
			return StepOutcome.of(BatchRunStatus.SKIPPED);
		}
		int created = houseCollectUnitLogic.prepare(today);
		progress.put("districts", HouseConstant.DISTRICTS.size());
		progress.put("created", created);
		return StepOutcome.of(BatchRunStatus.SUCCESS);
	}

	/**
	 * 수집 순서대로 단위를 받아 반영한다. 호출 예산이 떨어지면 남은 단위는 상태 그대로 두고 PARTIAL로 끝낸다.
	 * 이번 실행에서 쓴 호출 수는 예외가 나도 기록에 남는다(같은 날 다시 돌 때 예산에서 빼야 해서).
	 */
	private StepOutcome collectTrades(LocalDate today, StepProgress progress) {
		if (!tradeGateway.available()) {
			progress.put("reason", "SERVICE_KEY_MISSING");
			return StepOutcome.of(BatchRunStatus.SKIPPED);
		}
		long usedToday = houseBatchRunLogic.usedApiCalls(today, BatchStep.TRADE_COLLECT);
		CallBudget budget = new CallBudget("trade", houseProperties.getCallLimit().getTrade(), usedToday);
		progress.put("usedBeforeRun", usedToday);

		List<HouseCollectUnit> units = houseCollectUnitLogic.findDueUnits(today);
		progress.put("unitsDue", units.size());
		for (String key : List.of("unitsDone", "unitsFailed", "fetched", "inserted", "updated", "unchanged",
			"cancelled", "removed", "skippedRows", "emptyUnits", "complexCreated", "complexRefreshed")) {
			progress.put(key, 0);
		}

		String stoppedBy = null;
		try {
			for (HouseCollectUnit unit : units) {
				if (budget.remaining() <= 0) {
					stoppedBy = "BUDGET";
					break;
				}
				TradeFetchResult result = tradeGateway.fetchAll(unit.getLawdCd(), CollectUnitPriority.parse(unit.getDealYm()),
					budget);
				progress.apiCalls(budget.used());
				switch (result.outcome()) {
					case OK -> applyOrFail(today, unit, result, progress);
					case FAILED -> failUnit(unit, progress);
					case BUDGET_EXHAUSTED -> stoppedBy = "BUDGET";
					case QUOTA_EXCEEDED -> stoppedBy = "QUOTA";
					case KEY_REJECTED -> stoppedBy = "KEY_REJECTED";
				}
				if (stoppedBy != null) {
					break;
				}
			}
		} finally {
			progress.apiCalls(budget.used());
		}

		int done = (Integer)progress.detail().get("unitsDone");
		int failed = (Integer)progress.detail().get("unitsFailed");
		if (stoppedBy != null) {
			progress.put("stoppedBy", stoppedBy);
		}
		if ("KEY_REJECTED".equals(stoppedBy)) {
			return StepOutcome.of(BatchRunStatus.FAILED, "KEY_REJECTED");
		}
		if (stoppedBy != null) {
			return StepOutcome.of(BatchRunStatus.PARTIAL, stoppedBy);
		}
		if (failed > 0) {
			// 하나도 못 받고 전부 실패했으면 시작하자마자 실패한 것과 같다.
			return done == 0 ? StepOutcome.of(BatchRunStatus.FAILED, "ALL_UNITS_FAILED")
				: StepOutcome.of(BatchRunStatus.PARTIAL, "SOME_UNITS_FAILED");
		}
		return StepOutcome.of(BatchRunStatus.SUCCESS);
	}

	private void applyOrFail(LocalDate today, HouseCollectUnit unit, TradeFetchResult result, StepProgress progress) {
		TradeApplyResult applied;
		try {
			applied = houseTradeLogic.applyUnit(unit, result, today);
		} catch (RuntimeException e) {
			// 단위 트랜잭션은 이미 롤백됐다. 이 단위만 실패로 두고 다음 단위로 간다.
			log.error("실거래 단위 반영 실패: lawdCd={}, dealYm={}, error={}", unit.getLawdCd(), unit.getDealYm(),
				e.getClass().getSimpleName());
			failUnit(unit, progress);
			return;
		}
		progress.add("unitsDone", 1);
		progress.add("fetched", applied.fetched());
		progress.add("inserted", applied.inserted());
		progress.add("updated", applied.updated());
		progress.add("unchanged", applied.unchanged());
		progress.add("cancelled", applied.cancelled());
		progress.add("removed", applied.removed());
		progress.add("skippedRows", applied.skippedRows());
		progress.add("complexCreated", applied.complexCreated());
		progress.add("complexRefreshed", applied.complexRefreshed());
		if (applied.emptyResponse()) {
			progress.add("emptyUnits", 1);
		}
	}

	private void failUnit(HouseCollectUnit unit, StepProgress progress) {
		progress.add("unitsFailed", 1);
		try {
			houseCollectUnitLogic.markFailed(unit.getId());
		} catch (RuntimeException e) {
			log.error("실거래 단위 실패 표시 저장 실패: lawdCd={}, dealYm={}, error={}", unit.getLawdCd(),
				unit.getDealYm(), e.getClass().getSimpleName());
		}
	}
}
