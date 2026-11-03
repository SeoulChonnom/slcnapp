package com.seoulchonnom.aggregate.house.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexRedevelopmentId;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexRedevelopmentJpo;

/**
 * 지금은 읽기만 있다. 5단계 구현체가 생기면 학교와 같은 방식의 DELETE + INSERT를 여기에 둔다.
 */
@Repository
public interface HouseComplexRedevelopmentRepository
	extends JpaRepository<HouseComplexRedevelopmentJpo, HouseComplexRedevelopmentId> {
	/**
	 * 단지들의 반경 안 정비사업. 행은 [complex_id, name, project_type, stage, distance_m, source_url]이고
	 * 가까운 순, 이름 순이다. 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: = ANY(string_to_array(...)) 문법, 정렬(같은 단지 안의 거리 오름차순).
	 */
	@Query(value = "SELECT cr.complex_id, r.name, r.project_type, r.stage, cr.distance_m, r.source_url "
		+ "FROM slcn.house_complex_redevelopment cr JOIN slcn.house_redevelopment r ON r.id = cr.redevelopment_id "
		+ "WHERE cr.complex_id = ANY (string_to_array(CAST(:complexIds AS text), ',')) "
		+ "ORDER BY cr.complex_id, cr.distance_m, r.name",
		nativeQuery = true)
	List<Object[]> findByComplexIds(@Param("complexIds") String complexIds);
}
