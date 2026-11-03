package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBatchRunJpo;

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
}
