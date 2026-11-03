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
}
