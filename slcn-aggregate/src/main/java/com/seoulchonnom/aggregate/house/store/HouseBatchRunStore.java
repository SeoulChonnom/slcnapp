package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.util.Map;

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

	/**
	 * 그날 이 단계 기록의 detail[detailKey] 합계. 단계가 API를 둘 이상 쓸 때 API별 호출 수를 읽는다. 없으면 0이다.
	 */
	public long sumDetailCalls(LocalDate runDate, BatchStep step, String detailKey) {
		long sum = 0;
		for (var run : houseBatchRunRepository.findByRunDateAndStep(runDate, step)) {
			Map<String, Object> detail = run.getDetail();
			if (detail != null && detail.get(detailKey) instanceof Number number) {
				sum += number.longValue();
			}
		}
		return sum;
	}
}
