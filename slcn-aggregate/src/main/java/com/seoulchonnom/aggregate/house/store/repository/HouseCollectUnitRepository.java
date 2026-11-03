package com.seoulchonnom.aggregate.house.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseCollectUnitJpo;

@Repository
public interface HouseCollectUnitRepository extends JpaRepository<HouseCollectUnitJpo, String> {
	List<HouseCollectUnitJpo> findByDealYmBetween(String fromYm, String toYm);

	/**
	 * 구 × 계약년월 조합 중 없는 것만 만든다. 이미 있는 단위(진행 상태)는 건드리지 않는다.
	 * 구 코드와 계약년월은 쉼표로 이은 문자열 하나씩으로 받는다(빈 목록·배열 바인딩 문제를 피한다).
	 * 반환값은 새로 만든 행 수다.
	 *
	 * 사람이 검증할 지점: uk_house_collect_unit(lawd_cd, deal_ym) 제약이 실제로 생겼는지,
	 * 두 번 실행해도 0건이 나오는지, gen_random_uuid() 사용 가능 여부(PostgreSQL 13+).
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_collect_unit "
		+ "(id, entity_version, registered_time, modified_time, lawd_cd, deal_ym, status, attempts) "
		+ "SELECT gen_random_uuid()::text, 0, :now, :now, l.lawd_cd, y.deal_ym, 'NEVER', 0 "
		+ "FROM unnest(string_to_array(CAST(:lawdCds AS text), ',')) AS l(lawd_cd) "
		+ "CROSS JOIN unnest(string_to_array(CAST(:dealYms AS text), ',')) AS y(deal_ym) "
		+ "ON CONFLICT (lawd_cd, deal_ym) DO NOTHING",
		nativeQuery = true)
	int insertMissing(@Param("lawdCds") String lawdCds, @Param("dealYms") String dealYms, @Param("now") long now);

	/**
	 * 최근 3개월 수집 단위(구 25 x 3개월 = :required개)의 마지막 성공일 중 가장 이른 날짜.
	 * 단위가 모자라거나 한 번도 성공하지 못한 단위가 있으면 null. 반환은 날짜(java.sql.Date 등)라 Store가 LocalDate로 바꾼다.
	 * 사람이 검증할 지점: 75개 중 1개가 NEVER일 때 null, 모두 성공일 때 MIN, 단위가 74개일 때 null.
	 */
	@Query(value = "SELECT CASE WHEN COUNT(*) < :required OR COUNT(*) FILTER (WHERE last_success_date IS NULL) > 0 "
		+ "THEN NULL ELSE MIN(last_success_date) END "
		+ "FROM slcn.house_collect_unit WHERE deal_ym IN (:ym0, :ym1, :ym2)",
		nativeQuery = true)
	Object findDataAsOf(@Param("required") int required, @Param("ym0") String ym0, @Param("ym1") String ym1,
		@Param("ym2") String ym2);
}
