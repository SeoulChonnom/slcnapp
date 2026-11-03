package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.Optional;

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

	/**
	 * 그날 이 단계가 이미 쓴 호출 수 중 detail[detailKey]에 API별로 남긴 값. 단계가 API를 둘 이상 쓸 때의 차감 기준이다.
	 */
	public long usedDetailCalls(LocalDate runDate, BatchStep step, String detailKey) {
		return houseBatchRunStore.sumDetailCalls(runDate, step, detailKey);
	}

	/**
	 * 이 단계가 마지막으로 SUCCESS였던 실행일. SKIPPED는 SUCCESS가 아니므로 갱신 주기 판단에 섞이지 않는다.
	 */
	public Optional<LocalDate> findLastSuccessRunDate(BatchStep step) {
		return houseBatchRunStore.findLastSuccessRunDate(step);
	}
}
