package com.seoulchonnom.aggregate.house.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexSchoolId;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexSchoolJpo;

/**
 * 쓰기는 네이티브 DELETE + INSERT뿐이다. 읽기는 비교표 일괄 조회 하나.
 */
@Repository
public interface HouseComplexSchoolRepository extends JpaRepository<HouseComplexSchoolJpo, HouseComplexSchoolId> {
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_complex_school", nativeQuery = true)
	int deleteAllRows();

	/**
	 * 단지·학교급·학교·거리를 같은 길이의 쉼표 문자열 네 개로 받아 unnest로 한꺼번에 넣는다.
	 * 단지 ID(HOUSE_COMPLEX-xxxx)·학교급·학교 UUID·정수 거리에는 쉼표가 없다. 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: 다중 인자 unnest의 열 이름 지정, distance_m 정수 캐스트, 복합 PK 충돌이 없는지.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_complex_school (complex_id, level, school_id, distance_m) "
		+ "SELECT t.complex_id, t.level, t.school_id, CAST(t.distance_m AS integer) "
		+ "FROM unnest(string_to_array(CAST(:complexIds AS text), ','), "
		+ "            string_to_array(CAST(:levels AS text), ','), "
		+ "            string_to_array(CAST(:schoolIds AS text), ','), "
		+ "            string_to_array(CAST(:distances AS text), ',')) "
		+ "     AS t(complex_id, level, school_id, distance_m)",
		nativeQuery = true)
	int insertAll(@Param("complexIds") String complexIds, @Param("levels") String levels,
		@Param("schoolIds") String schoolIds, @Param("distances") String distances);

	/**
	 * 단지들의 최근접 학교. 행은 [complex_id, level, school_name, distance_m]이고 학교급 정렬은 Java에서 한다
	 * (문자열 정렬은 E, H, M이라 초·중·고 순이 아니다). 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: = ANY(string_to_array(...)) 문법, school_id가 가리키는 학교가 없는 행은 JOIN에서 빠지는지.
	 */
	@Query(value = "SELECT cs.complex_id, cs.level, s.name, cs.distance_m "
		+ "FROM slcn.house_complex_school cs JOIN slcn.house_school s ON s.id = cs.school_id "
		+ "WHERE cs.complex_id = ANY (string_to_array(CAST(:complexIds AS text), ','))",
		nativeQuery = true)
	List<Object[]> findByComplexIds(@Param("complexIds") String complexIds);
}
