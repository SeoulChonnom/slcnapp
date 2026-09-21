package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyJpo;
import com.seoulchonnom.aggregate.inspection.store.projection.MatchedPropertyPdo;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;

@Repository
public interface ViewedPropertyRepository extends JpaRepository<ViewedPropertyJpo, String> {
	/**
	 * sortOrder 중복을 허용하므로 registeredTime과 id를 2차 키로 붙인다.
	 * 없으면 동률 행의 순서를 PostgreSQL이 보장하지 않아 새로고침마다 화면이 뒤바뀐다.
	 */
	List<ViewedPropertyJpo> findAllByInspectionVisitIdOrderBySortOrderAscRegisteredTimeAscIdAsc(
		String inspectionVisitId);

	List<ViewedPropertyJpo> findAllByIdIn(Collection<String> ids);

	/**
	 * answers를 읽지 않는 projection 조회.
	 */
	List<ViewedPropertySummaryPdo> findAllByInspectionVisitIdIn(Collection<String> inspectionVisitIds);

	long countByInspectionVisitId(String inspectionVisitId);

	void deleteByInspectionVisitId(String inspectionVisitId);

	/**
	 * 지역 목록 검색(A-③)의 matchedProperty 후보. areaIds로 이미 페이징된 현재 페이지
	 * 지역만 훑으므로 전건 스캔이 아니다. ILIKE라 대소문자를 구분하지 않는다.
	 *
	 * 사람이 검증할 지점: viewed_property.inspection_visit_id -> inspection_visit.id 조인 키,
	 * inspection_visit.area_id 존재 여부, complex_name/name 컬럼명.
	 */
	@Query(value = "SELECT p.id AS id, p.complex_name AS complexName, p.name AS name, "
		+ "p.interest_level AS interestLevel, p.sort_order AS sortOrder, "
		+ "v.id AS visitId, v.visited_at AS visitedAt, v.area_id AS areaId "
		+ "FROM slcn.viewed_property p "
		+ "JOIN slcn.inspection_visit v ON v.id = p.inspection_visit_id "
		+ "WHERE v.area_id IN (:areaIds) "
		+ "AND (p.complex_name ILIKE :likeKeyword OR p.name ILIKE :likeKeyword)",
		nativeQuery = true)
	List<MatchedPropertyPdo> findMatchedByAreaIdsAndKeyword(@Param("areaIds") Collection<String> areaIds,
		@Param("likeKeyword") String likeKeyword);
}
