package com.seoulchonnom.aggregate.house.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexRedevelopmentId;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexRedevelopmentJpo;

/**
 * 쓰기는 학교와 같은 방식의 네이티브 DELETE + INSERT다. 정비사업 구역이 지워질 때는 그 구역의 행만 지운다.
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

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_complex_redevelopment", nativeQuery = true)
	int deleteAllRows();

	/**
	 * 지워지는 구역의 연결만 지운다. 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_complex_redevelopment "
		+ "WHERE redevelopment_id = ANY (string_to_array(CAST(:redevelopmentIds AS text), ','))",
		nativeQuery = true)
	int deleteByRedevelopmentIds(@Param("redevelopmentIds") String redevelopmentIds);

	/**
	 * 단지·구역·거리를 같은 길이의 쉼표 문자열 셋으로 받아 unnest로 한꺼번에 넣는다(학교 최근접과 같은 방식).
	 * 단지 ID(HOUSE_COMPLEX-xxxx)·구역 UUID·정수 거리에는 쉼표가 없다. 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: 다중 인자 unnest의 열 이름 지정, distance_m 정수 캐스트, 복합 PK 충돌이 없는지.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_complex_redevelopment (complex_id, redevelopment_id, distance_m) "
		+ "SELECT t.complex_id, t.redevelopment_id, CAST(t.distance_m AS integer) "
		+ "FROM unnest(string_to_array(CAST(:complexIds AS text), ','), "
		+ "            string_to_array(CAST(:redevelopmentIds AS text), ','), "
		+ "            string_to_array(CAST(:distances AS text), ',')) "
		+ "     AS t(complex_id, redevelopment_id, distance_m)",
		nativeQuery = true)
	int insertAll(@Param("complexIds") String complexIds, @Param("redevelopmentIds") String redevelopmentIds,
		@Param("distances") String distances);
}
