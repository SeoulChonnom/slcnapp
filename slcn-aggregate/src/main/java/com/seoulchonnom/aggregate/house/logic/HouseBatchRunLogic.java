package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseBatchRunLogic {
	private final HouseBatchRunStore houseBatchRunStore;

	/**
	 * 단계 기록은 실행 결과와 별개 트랜잭션으로 저장한다. 단계가 실패해도 기록은 남아야 한다.
	 */
	@Transactional
	public HouseBatchRun save(HouseBatchRun run) {
		return houseBatchRunStore.save(run);
	}

	/**
	 * 그날 이 단계가 이미 쓴 외부 호출 수. 호출 예산에서 빼는 값이다.
	 */
	public long usedApiCalls(LocalDate runDate, BatchStep step) {
		return houseBatchRunStore.sumApiCalls(runDate, step);
	}
}
