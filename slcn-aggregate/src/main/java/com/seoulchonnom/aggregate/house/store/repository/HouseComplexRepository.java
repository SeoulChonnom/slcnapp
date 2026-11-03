package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexJpo;

@Repository
public interface HouseComplexRepository extends JpaRepository<HouseComplexJpo, String> {
	List<HouseComplexJpo> findByAptSeqIn(Collection<String> aptSeqs);

	/**
	 * 좌표 대상. 좌표가 PENDING인 단지를 최근 12개월 거래 건수 내림차순(화면에 자주 나올 단지부터)으로.
	 * 거래 건수는 해제·원천 삭제 거래를 뺀다. 거래가 없는 단지는 0건으로 맨 뒤다.
	 * 사람이 검증할 지점: LEFT JOIN 서브쿼리 정렬, LIMIT 바인딩, c.*가 HouseComplexJpo로 매핑되는지.
	 */
	@Query(value = "SELECT c.* FROM slcn.house_complex c "
		+ "LEFT JOIN (SELECT t.complex_id AS complex_id, COUNT(*) AS cnt FROM slcn.house_trade t "
		+ "           WHERE t.cancelled = false AND t.removed = false AND t.deal_date > CAST(:since AS date) "
		+ "           GROUP BY t.complex_id) s ON s.complex_id = c.id "
		+ "WHERE c.geocode_status = 'PENDING' "
		+ "ORDER BY COALESCE(s.cnt, 0) DESC, c.id "
		+ "LIMIT :limit",
		nativeQuery = true)
	List<HouseComplexJpo> findGeocodeTargets(@Param("since") LocalDate since, @Param("limit") int limit);

	/**
	 * 건축물대장 대상 1순위. 대장이 PENDING인 단지를 최근 12개월 거래 건수 내림차순으로.
	 */
	@Query(value = "SELECT c.* FROM slcn.house_complex c "
		+ "LEFT JOIN (SELECT t.complex_id AS complex_id, COUNT(*) AS cnt FROM slcn.house_trade t "
		+ "           WHERE t.cancelled = false AND t.removed = false AND t.deal_date > CAST(:since AS date) "
		+ "           GROUP BY t.complex_id) s ON s.complex_id = c.id "
		+ "WHERE c.register_status = 'PENDING' "
		+ "ORDER BY COALESCE(s.cnt, 0) DESC, c.id "
		+ "LIMIT :limit",
		nativeQuery = true)
	List<HouseComplexJpo> findRegisterPendingTargets(@Param("since") LocalDate since, @Param("limit") int limit);

	/**
	 * 건축물대장 대상 2순위. 이미 DONE이고 마지막 동기화가 renewBefore 이전(30일 이상 지남)인 단지를 오래된 순으로.
	 */
	@Query(value = "SELECT c.* FROM slcn.house_complex c "
		+ "WHERE c.register_status = 'DONE' AND c.register_synced_date <= CAST(:renewBefore AS date) "
		+ "ORDER BY c.register_synced_date ASC, c.id "
		+ "LIMIT :limit",
		nativeQuery = true)
	List<HouseComplexJpo> findRegisterRenewalTargets(@Param("renewBefore") LocalDate renewBefore,
		@Param("limit") int limit);

	/**
	 * 보강 실패 단지의 id와 최근 12개월 거래 건수. 건수 내림차순, 그다음 id.
	 * 행은 [id(String), trades12m(Number)]다. 엔티티는 호출한 쪽이 findAllById로 가져온다.
	 * 사람이 검증할 지점: OFFSET/LIMIT 바인딩, trades12m이 Long으로 오는지, 정렬이 화면 순서와 같은지.
	 */
	@Query(value = "SELECT c.id AS id, COALESCE(s.cnt, 0) AS trades12m FROM slcn.house_complex c "
		+ "LEFT JOIN (SELECT t.complex_id AS complex_id, COUNT(*) AS cnt FROM slcn.house_trade t "
		+ "           WHERE t.cancelled = false AND t.removed = false AND t.deal_date > CAST(:since AS date) "
		+ "           GROUP BY t.complex_id) s ON s.complex_id = c.id "
		+ "WHERE c.geocode_status = 'FAILED' OR c.register_status = 'FAILED' "
		+ "ORDER BY COALESCE(s.cnt, 0) DESC, c.id "
		+ "LIMIT :limit OFFSET :offset",
		nativeQuery = true)
	List<Object[]> findIncompleteIdsWithTrades(@Param("since") LocalDate since, @Param("limit") int limit,
		@Param("offset") int offset);

	@Query(value = "SELECT COUNT(*) FROM slcn.house_complex c "
		+ "WHERE c.geocode_status = 'FAILED' OR c.register_status = 'FAILED'",
		nativeQuery = true)
	long countIncomplete();

	/**
	 * 단지 하나의 최근 12개월 거래 건수(해제·원천 삭제 제외). 보정 응답의 trades12m에 쓴다.
	 */
	@Query(value = "SELECT COUNT(*) FROM slcn.house_trade t "
		+ "WHERE t.complex_id = :complexId AND t.cancelled = false AND t.removed = false "
		+ "AND t.deal_date > CAST(:since AS date)",
		nativeQuery = true)
	long countRecentTrades(@Param("complexId") String complexId, @Param("since") LocalDate since);
}
