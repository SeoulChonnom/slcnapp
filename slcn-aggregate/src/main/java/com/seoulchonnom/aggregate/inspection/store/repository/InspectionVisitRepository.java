package com.seoulchonnom.aggregate.inspection.store.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitJpo;

@Repository
public interface InspectionVisitRepository extends JpaRepository<InspectionVisitJpo, String> {
	List<InspectionVisitJpo> findAllByAreaIdOrderByVisitedAtDescIdAsc(String areaId);

	List<InspectionVisitJpo> findAllByAreaIdIn(Collection<String> areaIds);

	List<InspectionVisitJpo> findAllByIdIn(Collection<String> ids);

	boolean existsByAreaId(String areaId);

	/**
	 * 임장 목록(C-2)의 필터·페이징 전체를 DB로 내린다. 예전에는 findAll()/findAllByAreaId()로
	 * 전건을 읽어 자바 스트림으로 걸렀는데, 그 상태에서 LIMIT만 붙이면 "DB가 50건을 주고
	 * 메모리 필터가 12건만 남기는" 식으로 페이지 크기가 들쭉날쭉해지고 hasNext 판정이 깨진다.
	 *
	 * (:param IS NULL OR 조건) 패턴으로 6개 필터를 전부 선택적으로 만든다. 태그는 AND라
	 * HAVING COUNT(DISTINCT ...) = :tagCount로 "요청한 태그를 전부 가진 임장"만 남긴다.
	 * tagCount=0(태그 미지정)이면 HAVING이 항상 참이 되어 검사를 건너뛴다 — 이때 tagNames는
	 * 실제 태그명으로 절대 쓰이지 않는 자리표시 값 하나를 담아야 한다("IN ()"은 SQL 문법
	 * 오류이기 때문). InspectionVisitStore.normalizeTagNames가 이 자리표시 값을 만든다.
	 *
	 * SELECT v.*와 GROUP BY v.id를 같이 쓸 수 있는 이유: v.id가 inspection_visit의 기본키라
	 * PostgreSQL이 함수적 종속성으로 나머지 v.* 컬럼을 GROUP BY 없이도 허용한다(표준 SQL
	 * 동작이며 MySQL의 ONLY_FULL_GROUP_BY 완화와는 다른, PostgreSQL의 정식 지원 기능).
	 *
	 * 사람이 검증할 지점: v.* 컬럼과 InspectionVisitJpo 필드가 1:1로 맞는지(엔티티로 직접
	 * 매핑하므로 컬럼이 하나라도 어긋나면 매핑이 깨진다), status/revisit_intent 비교가
	 * 문자열 컬럼과 맞는지, tag.name IN (:tagNames)의 대소문자 일치 여부(여기는 ILIKE가 아니라
	 * 완전 일치다 — 태그는 자동완성으로 선택된 값이 그대로 온다는 전제), LIMIT/OFFSET 바인딩.
	 */
	@Query(value = "SELECT v.* FROM slcn.inspection_visit v "
		+ "LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "LEFT JOIN slcn.inspection_tag t ON t.id = ivt.tag_id "
		+ "WHERE (CAST(:areaId AS varchar) IS NULL OR v.area_id = :areaId) "
		+ "AND (CAST(:status AS varchar) IS NULL OR v.status = :status) "
		+ "AND (CAST(:revisitIntent AS varchar) IS NULL OR v.revisit_intent = :revisitIntent) "
		+ "AND (CAST(:fromDate AS timestamp) IS NULL OR v.visited_at >= CAST(:fromDate AS timestamp)) "
		+ "AND (CAST(:toDate AS timestamp) IS NULL OR v.visited_at <= CAST(:toDate AS timestamp)) "
		+ "GROUP BY v.id "
		+ "HAVING (:tagCount = 0 OR COUNT(DISTINCT CASE WHEN t.name IN (:tagNames) THEN t.name END) = :tagCount) "
		+ "ORDER BY v.visited_at DESC, v.id ASC "
		+ "LIMIT :limit OFFSET :offset",
		nativeQuery = true)
	List<InspectionVisitJpo> findFiltered(@Param("areaId") String areaId, @Param("status") String status,
		@Param("revisitIntent") String revisitIntent, @Param("fromDate") LocalDateTime fromDate,
		@Param("toDate") LocalDateTime toDate, @Param("tagNames") List<String> tagNames,
		@Param("tagCount") int tagCount, @Param("limit") int limit, @Param("offset") int offset);

	/**
	 * findFiltered와 같은 필터로 매칭 건수만 센다(페이지 응답의 totalCount/hasNext용).
	 * HAVING이 있는 집계 쿼리를 그대로 COUNT(*)로 감쌀 수 없어 서브쿼리로 감쌌다.
	 */
	@Query(value = "SELECT COUNT(*) FROM ("
		+ "  SELECT v.id FROM slcn.inspection_visit v "
		+ "  LEFT JOIN slcn.inspection_visit_tag ivt ON ivt.inspection_visit_id = v.id "
		+ "  LEFT JOIN slcn.inspection_tag t ON t.id = ivt.tag_id "
		+ "  WHERE (CAST(:areaId AS varchar) IS NULL OR v.area_id = :areaId) "
		+ "  AND (CAST(:status AS varchar) IS NULL OR v.status = :status) "
		+ "  AND (CAST(:revisitIntent AS varchar) IS NULL OR v.revisit_intent = :revisitIntent) "
		+ "  AND (CAST(:fromDate AS timestamp) IS NULL OR v.visited_at >= CAST(:fromDate AS timestamp)) "
		+ "  AND (CAST(:toDate AS timestamp) IS NULL OR v.visited_at <= CAST(:toDate AS timestamp)) "
		+ "  GROUP BY v.id "
		+ "  HAVING (:tagCount = 0 OR COUNT(DISTINCT CASE WHEN t.name IN (:tagNames) THEN t.name END) = :tagCount)"
		+ ") matched",
		nativeQuery = true)
	long countFiltered(@Param("areaId") String areaId, @Param("status") String status,
		@Param("revisitIntent") String revisitIntent, @Param("fromDate") LocalDateTime fromDate,
		@Param("toDate") LocalDateTime toDate, @Param("tagNames") List<String> tagNames,
		@Param("tagCount") int tagCount);
}
