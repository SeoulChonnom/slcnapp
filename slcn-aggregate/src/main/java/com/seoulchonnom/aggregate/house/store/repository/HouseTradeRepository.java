package com.seoulchonnom.aggregate.house.store.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseTradeJpo;
import com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo;

/**
 * 거래 쓰기는 JPA save를 쓰지 않는다. 기존 행을 먼저 SELECT해야 해서 행마다 두 번 왕복하고,
 * ON CONFLICT 한 문장이면 동시성도 DB가 보장한다. 네이티브 쿼리는 JPA @Version을 거치지 않으므로
 * entity_version은 INSERT 시 0, UPDATE 시 +1로 직접 쓴다.
 */
@Repository
public interface HouseTradeRepository extends JpaRepository<HouseTradeJpo, String> {
	/**
	 * 같은 dedup_key가 있으면 해제 여부·거래 유형·removed만 갱신하되, 값이 실제로 달라졌을 때만 UPDATE한다.
	 * 값이 같으면 WHERE에서 걸려 행이 바뀌지 않으므로 modified_time/entity_version이 그대로다.
	 * RETURNING은 INSERT면 true, UPDATE면 false 한 행을 주고, 변경이 없으면 행이 없다.
	 *
	 * 사람이 검증할 지점: uk_house_trade_dedup_key 제약이 실제로 생겼는지, 같은 응답을 두 번 넣었을 때
	 * 두 번째가 빈 목록을 돌려주고 entity_version이 안 변하는지, RETURNING (xmax = 0) 동작.
	 */
	@Query(value = "INSERT INTO slcn.house_trade "
		+ "(id, entity_version, registered_time, modified_time, dedup_key, complex_id, lawd_cd, deal_ym, deal_date, "
		+ " exclusive_area, area_bucket, amount_manwon, floor, apt_dong, build_year, dealing_type, "
		+ " cancelled, cancelled_date, outlier, removed) "
		+ "VALUES (:id, 0, :now, :now, :dedupKey, :complexId, :lawdCd, :dealYm, :dealDate, "
		+ " :exclusiveArea, :areaBucket, :amountManwon, CAST(:floor AS integer), CAST(:aptDong AS varchar), "
		+ " CAST(:buildYear AS integer), :dealingType, :cancelled, CAST(:cancelledDate AS date), false, false) "
		+ "ON CONFLICT (dedup_key) DO UPDATE SET "
		+ " cancelled = EXCLUDED.cancelled, cancelled_date = EXCLUDED.cancelled_date, "
		+ " dealing_type = EXCLUDED.dealing_type, removed = false, "
		+ " modified_time = EXCLUDED.modified_time, entity_version = slcn.house_trade.entity_version + 1 "
		+ "WHERE slcn.house_trade.cancelled IS DISTINCT FROM EXCLUDED.cancelled "
		+ " OR slcn.house_trade.cancelled_date IS DISTINCT FROM EXCLUDED.cancelled_date "
		+ " OR slcn.house_trade.dealing_type IS DISTINCT FROM EXCLUDED.dealing_type "
		+ " OR slcn.house_trade.removed = true "
		+ "RETURNING (xmax = 0)",
		nativeQuery = true)
	List<Boolean> upsert(@Param("id") String id, @Param("now") long now, @Param("dedupKey") String dedupKey,
		@Param("complexId") String complexId, @Param("lawdCd") String lawdCd, @Param("dealYm") String dealYm,
		@Param("dealDate") LocalDate dealDate, @Param("exclusiveArea") BigDecimal exclusiveArea,
		@Param("areaBucket") String areaBucket, @Param("amountManwon") long amountManwon,
		@Param("floor") Integer floor, @Param("aptDong") String aptDong, @Param("buildYear") Integer buildYear,
		@Param("dealingType") String dealingType, @Param("cancelled") boolean cancelled,
		@Param("cancelledDate") LocalDate cancelledDate);

	/**
	 * 이번 응답에 없는 거래를 원천 삭제로 표시한다. 응답이 모든 페이지까지 성공했을 때만 부른다.
	 * 키 목록은 쉼표로 이은 문자열 하나로 받는다(키는 hex라 쉼표가 없다). 빈 문자열이면 모든 행이
	 * 지워진 것으로 표시되므로 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 *
	 * 사람이 검증할 지점: string_to_array + <> ALL 문법, 대상 단위 밖의 행이 바뀌지 않는지,
	 * 이미 removed인 행을 다시 건드리지 않는지.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE slcn.house_trade SET removed = true, modified_time = :now, entity_version = entity_version + 1 "
		+ "WHERE lawd_cd = :lawdCd AND deal_ym = :dealYm AND removed = false "
		+ "AND dedup_key <> ALL (string_to_array(CAST(:keys AS text), ','))",
		nativeQuery = true)
	int markRemovedExcept(@Param("lawdCd") String lawdCd, @Param("dealYm") String dealYm,
		@Param("keys") String keys, @Param("now") long now);

	/**
	 * 이상치 판정 대상. 해제·원천 삭제가 아닌 B59/B84 거래를 필요한 컬럼만 읽는다(약 20만 행 규모).
	 * JPQL 생성자 표현식이라 날짜·enum이 타입대로 온다.
	 */
	@Query("select new com.seoulchonnom.aggregate.house.store.projection.OutlierCandidatePdo("
		+ "t.id, t.complexId, t.areaBucket, t.dealDate, t.amountManwon) "
		+ "from HouseTradeJpo t "
		+ "where t.areaBucket in (com.seoulchonnom.spec.house.entity.vo.AreaBucket.B59, "
		+ "com.seoulchonnom.spec.house.entity.vo.AreaBucket.B84) "
		+ "and t.cancelled = false and t.removed = false and t.dealDate > :since")
	List<OutlierCandidatePdo> findOutlierCandidates(@Param("since") LocalDate since);

	/**
	 * 기준일 이후 거래의 이상치 표시를 모두 푼다. 이어서 markOutliers로 다시 찍는다(같은 트랜잭션).
	 * 파생값이라 entity_version/modified_time은 올리지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE slcn.house_trade SET outlier = false "
		+ "WHERE outlier = true AND deal_date > CAST(:since AS date)",
		nativeQuery = true)
	int clearOutliers(@Param("since") LocalDate since);

	/**
	 * id를 쉼표로 이은 문자열 하나로 받는다(UUID라 쉼표가 없다). 빈 문자열이면 아무 행도 맞지 않지만
	 * 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE slcn.house_trade SET outlier = true "
		+ "WHERE id = ANY (string_to_array(CAST(:ids AS text), ','))",
		nativeQuery = true)
	int markOutliers(@Param("ids") String ids);
}
