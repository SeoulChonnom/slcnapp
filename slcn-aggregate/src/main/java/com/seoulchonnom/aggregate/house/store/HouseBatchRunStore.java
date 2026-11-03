package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.mapper.HouseBatchRunJpoMapper;
import com.seoulchonnom.aggregate.house.store.repository.HouseBatchRunRepository;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HouseBatchRunStore {
	private final HouseBatchRunRepository houseBatchRunRepository;
	private final HouseBatchRunJpoMapper houseBatchRunJpoMapper;

	public HouseBatchRun save(HouseBatchRun run) {
		return houseBatchRunJpoMapper.toDomain(houseBatchRunRepository.save(houseBatchRunJpoMapper.toJpo(run)));
	}

	/**
	 * 그날 이 단계가 이미 쓴 호출 수 합계. 기록이 없으면 0이다.
	 */
	public long sumApiCalls(LocalDate runDate, BatchStep step) {
		return houseBatchRunRepository.sumApiCalls(runDate, step.name());
	}
}
