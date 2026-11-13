package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBatchRunJpo;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

@Repository
public interface HouseBatchRunRepository extends JpaRepository<HouseBatchRunJpo, String> {
	/**
	 * 그날 이 단계가 이미 쓴 호출 수 합계. 같은 날 두 번 돌아도 호출 예산을 넘지 않게 차감하는 데 쓴다.
	 * 사람이 검증할 지점: step 컬럼이 문자열(EnumType.STRING)로 저장되는지, 기록이 없을 때 0이 나오는지.
	 */
	@Query(value = "SELECT COALESCE(SUM(r.api_calls), 0) FROM slcn.house_batch_run r "
		+ "WHERE r.run_date = :runDate AND r.step = :step",
		nativeQuery = true)
	long sumApiCalls(@Param("runDate") LocalDate runDate, @Param("step") String step);

	/**
	 * 그날 이 단계의 기록 전부. 단계 안에서 API가 둘 이상일 때 detail의 API별 호출 수를 Java에서 합산하려고 쓴다.
	 * 하루 한 단계의 기록은 실행 횟수만큼(보통 1~2행)이라 JSON을 SQL로 파싱하지 않는다.
	 */
	List<HouseBatchRunJpo> findByRunDateAndStep(LocalDate runDate, BatchStep step);

	/**
	 * 이 단계의 해당 상태 기록 중 가장 최근 실행일의 한 행.
	 */
	Optional<HouseBatchRunJpo> findTopByStepAndStatusOrderByRunDateDesc(BatchStep step, BatchRunStatus status);
}
