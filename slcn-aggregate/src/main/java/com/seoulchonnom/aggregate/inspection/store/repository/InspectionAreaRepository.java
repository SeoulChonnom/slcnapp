package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionAreaJpo;

/**
 * 지역 목록 정렬(RECENT_VISIT/VISIT_COUNT/TOP_INTEREST)은 inspection_area 행에 없는
 * 집계값(방문 최신순/횟수/최고 관심도) 기준이라, 정렬·검색·페이징을 전부 DB에서 하는
 * 네이티브 쿼리 3종 + 매칭 카운트 1종을 둔다. QueryDSL 등 동적 쿼리 빌더는 도입하지 않는다.
 *
 * 5종 검색 범위(지역명/설명/단지명/매물명/태그명)를 다 훑으려면 viewed_property와
 * 태그 두 연결 테이블까지 LEFT JOIN해야 한다. LEFT JOIN이라 이 조인들이 fan-out을 만들 수
 * 있으므로, COUNT는 항상 COUNT(DISTINCT v.id)/COUNT(DISTINCT a.id)로 쓴다 — 아니면
 * 매물·태그가 여러 건인 임장/지역의 카운트가 부풀려진다. MAX() 계열은 fan-out에 영향받지
 * 않아 그대로 둬도 된다.
 */
@Repository
public interface InspectionAreaRepository extends JpaRepository<InspectionAreaJpo, String> {
	List<InspectionAreaJpo> findAllByHiddenFalseOrderByNameAsc();

	List<InspectionAreaJpo> findAllByHiddenFalseAndNameContainingOrderByNameAsc(String keyword);

	List<InspectionAreaJpo> findAllByIdIn(Collection<String> ids);

	Optional<InspectionAreaJpo> findByName(String name);

	long countByHiddenFalse();

	/**
	 * 사람이 검증할 지점: LEFT JOIN 체인(visit -> viewed_property, visit_tag/tag,
	 * property_tag/tag)의 조인 키, ILIKE 대소문자 무시 동작, revisit_intent 서브쿼리의
	 * ORDER BY visited_at DESC, id ASC(동점 시 등록순이 아니라 id 순으로 안정적 정렬),
	 * NULLS LAST가 PostgreSQL 문법인지, LIMIT/OFFSET 파라미터 바인딩.
	 */
	@Query(value = "SELECT a.id FROM slcn.inspection_area a "
		+ "LEFT JOIN slcn.inspection_visit v ON v.area_id = a.id "
		+ "LEFT JOIN slcn.viewed_property p ON p.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_tag vtag ON vtag.id = ivt.tag_id "
		+ "LEFT JOIN slcn.viewed_property_tag vpt ON vpt.viewed_property_id = p.id "
		+ "LEFT JOIN slcn.inspection_tag ptag ON ptag.id = vpt.tag_id "
		+ "WHERE a.hidden = false "
		+ "AND (:likeKeyword IS NULL OR ("
		+ "  a.name ILIKE :likeKeyword OR a.description ILIKE :likeKeyword "
		+ "  OR p.complex_name ILIKE :likeKeyword OR p.name ILIKE :likeKeyword "
		+ "  OR vtag.name ILIKE :likeKeyword OR ptag.name ILIKE :likeKeyword"
		+ ")) "
		+ "AND (:revisitIntent IS NULL OR ("
		+ "  SELECT iv2.revisit_intent FROM slcn.inspection_visit iv2 WHERE iv2.area_id = a.id "
		+ "  ORDER BY iv2.visited_at DESC, iv2.id ASC LIMIT 1"
		+ ") = :revisitIntent) "
		+ "GROUP BY a.id, a.name "
		+ "ORDER BY MAX(v.visited_at) DESC NULLS LAST, a.name ASC "
		+ "LIMIT :limit OFFSET :offset",
		nativeQuery = true)
	List<String> findAreaIdsOrderByRecentVisit(@Param("likeKeyword") String likeKeyword,
		@Param("revisitIntent") String revisitIntent, @Param("limit") int limit, @Param("offset") int offset);

	/**
	 * RECENT_VISIT과 조인/필터가 같고 정렬 기준만 COUNT(DISTINCT v.id)다.
	 * 사람이 검증할 지점: fan-out(매물·태그 조인)이 있는 상태에서 COUNT(DISTINCT v.id)가
	 * 방문 "행 수"가 아니라 "고유 임장 수"를 세는지.
	 */
	@Query(value = "SELECT a.id FROM slcn.inspection_area a "
		+ "LEFT JOIN slcn.inspection_visit v ON v.area_id = a.id "
		+ "LEFT JOIN slcn.viewed_property p ON p.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_tag vtag ON vtag.id = ivt.tag_id "
		+ "LEFT JOIN slcn.viewed_property_tag vpt ON vpt.viewed_property_id = p.id "
		+ "LEFT JOIN slcn.inspection_tag ptag ON ptag.id = vpt.tag_id "
		+ "WHERE a.hidden = false "
		+ "AND (:likeKeyword IS NULL OR ("
		+ "  a.name ILIKE :likeKeyword OR a.description ILIKE :likeKeyword "
		+ "  OR p.complex_name ILIKE :likeKeyword OR p.name ILIKE :likeKeyword "
		+ "  OR vtag.name ILIKE :likeKeyword OR ptag.name ILIKE :likeKeyword"
		+ ")) "
		+ "AND (:revisitIntent IS NULL OR ("
		+ "  SELECT iv2.revisit_intent FROM slcn.inspection_visit iv2 WHERE iv2.area_id = a.id "
		+ "  ORDER BY iv2.visited_at DESC, iv2.id ASC LIMIT 1"
		+ ") = :revisitIntent) "
		+ "GROUP BY a.id, a.name "
		+ "ORDER BY COUNT(DISTINCT v.id) DESC, a.name ASC "
		+ "LIMIT :limit OFFSET :offset",
		nativeQuery = true)
	List<String> findAreaIdsOrderByVisitCount(@Param("likeKeyword") String likeKeyword,
		@Param("revisitIntent") String revisitIntent, @Param("limit") int limit, @Param("offset") int offset);

	/**
	 * RECENT_VISIT과 조인/필터가 같고 정렬 기준만 MAX(p.interest_level)다.
	 * 사람이 검증할 지점: interest_level 컬럼명, NULLS LAST 동작.
	 */
	@Query(value = "SELECT a.id FROM slcn.inspection_area a "
		+ "LEFT JOIN slcn.inspection_visit v ON v.area_id = a.id "
		+ "LEFT JOIN slcn.viewed_property p ON p.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_tag vtag ON vtag.id = ivt.tag_id "
		+ "LEFT JOIN slcn.viewed_property_tag vpt ON vpt.viewed_property_id = p.id "
		+ "LEFT JOIN slcn.inspection_tag ptag ON ptag.id = vpt.tag_id "
		+ "WHERE a.hidden = false "
		+ "AND (:likeKeyword IS NULL OR ("
		+ "  a.name ILIKE :likeKeyword OR a.description ILIKE :likeKeyword "
		+ "  OR p.complex_name ILIKE :likeKeyword OR p.name ILIKE :likeKeyword "
		+ "  OR vtag.name ILIKE :likeKeyword OR ptag.name ILIKE :likeKeyword"
		+ ")) "
		+ "AND (:revisitIntent IS NULL OR ("
		+ "  SELECT iv2.revisit_intent FROM slcn.inspection_visit iv2 WHERE iv2.area_id = a.id "
		+ "  ORDER BY iv2.visited_at DESC, iv2.id ASC LIMIT 1"
		+ ") = :revisitIntent) "
		+ "GROUP BY a.id, a.name "
		+ "ORDER BY MAX(p.interest_level) DESC NULLS LAST, a.name ASC "
		+ "LIMIT :limit OFFSET :offset",
		nativeQuery = true)
	List<String> findAreaIdsOrderByTopInterest(@Param("likeKeyword") String likeKeyword,
		@Param("revisitIntent") String revisitIntent, @Param("limit") int limit, @Param("offset") int offset);

	/**
	 * 페이지 응답의 totalCount용. 정렬 없이 같은 필터로 매칭 지역 수만 센다.
	 */
	@Query(value = "SELECT COUNT(DISTINCT a.id) FROM slcn.inspection_area a "
		+ "LEFT JOIN slcn.inspection_visit v ON v.area_id = a.id "
		+ "LEFT JOIN slcn.viewed_property p ON p.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_tag vtag ON vtag.id = ivt.tag_id "
		+ "LEFT JOIN slcn.viewed_property_tag vpt ON vpt.viewed_property_id = p.id "
		+ "LEFT JOIN slcn.inspection_tag ptag ON ptag.id = vpt.tag_id "
		+ "WHERE a.hidden = false "
		+ "AND (:likeKeyword IS NULL OR ("
		+ "  a.name ILIKE :likeKeyword OR a.description ILIKE :likeKeyword "
		+ "  OR p.complex_name ILIKE :likeKeyword OR p.name ILIKE :likeKeyword "
		+ "  OR vtag.name ILIKE :likeKeyword OR ptag.name ILIKE :likeKeyword"
		+ ")) "
		+ "AND (:revisitIntent IS NULL OR ("
		+ "  SELECT iv2.revisit_intent FROM slcn.inspection_visit iv2 WHERE iv2.area_id = a.id "
		+ "  ORDER BY iv2.visited_at DESC, iv2.id ASC LIMIT 1"
		+ ") = :revisitIntent)",
		nativeQuery = true)
	long countMatchingAreas(@Param("likeKeyword") String likeKeyword, @Param("revisitIntent") String revisitIntent);

	/**
	 * 전역 요약(totals.visitCount)용. hidden=false인 지역에 속한 임장만 센다.
	 */
	@Query(value = "SELECT COUNT(*) FROM slcn.inspection_visit v "
		+ "JOIN slcn.inspection_area a ON a.id = v.area_id WHERE a.hidden = false",
		nativeQuery = true)
	long countVisitsOfVisibleAreas();

	/**
	 * 전역 요약(totals.propertyCount)용. hidden=false인 지역에 속한 매물만 센다.
	 */
	@Query(value = "SELECT COUNT(*) FROM slcn.viewed_property p "
		+ "JOIN slcn.inspection_visit v ON v.id = p.inspection_visit_id "
		+ "JOIN slcn.inspection_area a ON a.id = v.area_id WHERE a.hidden = false",
		nativeQuery = true)
	long countPropertiesOfVisibleAreas();

	/**
	 * 필터 칩 카운트(revisitIntentCounts)용. 지역별로 "최신 회차 1건"만 골라(DISTINCT ON)
	 * 그 회차의 revisit_intent로 지역 수를 센다. 임장이 0건이거나 최신 회차의 revisit_intent가
	 * null인 지역은 intent 컬럼이 null인 그룹으로 잡힌다 — Store에서 그 그룹은 버리고
	 * total(=전체 지역 수)만 별도로 쓴다.
	 *
	 * 사람이 검증할 지점: DISTINCT ON (iv.area_id) ... ORDER BY iv.area_id, iv.visited_at DESC, iv.id ASC
	 * 가 PostgreSQL에서 "지역별 최신 회차 1건"을 정확히 고르는지, revisit_intent 컬럼명.
	 */
	@Query(value = "WITH latest_visit AS ("
		+ "  SELECT DISTINCT ON (iv.area_id) iv.area_id, iv.revisit_intent "
		+ "  FROM slcn.inspection_visit iv "
		+ "  ORDER BY iv.area_id, iv.visited_at DESC, iv.id ASC"
		+ ") "
		+ "SELECT lv.revisit_intent AS intent, COUNT(*) AS cnt "
		+ "FROM slcn.inspection_area a "
		+ "LEFT JOIN latest_visit lv ON lv.area_id = a.id "
		+ "WHERE a.hidden = false "
		+ "GROUP BY lv.revisit_intent",
		nativeQuery = true)
	List<Object[]> countAreasByLatestRevisitIntent();
}
