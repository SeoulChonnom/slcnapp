package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexAreaStatId;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexAreaStatJpo;

/**
 * 쓰기는 네이티브 DELETE + INSERT…SELECT 뿐이다. 읽기는 findById(복합 키).
 */
@Repository
public interface HouseComplexAreaStatRepository extends JpaRepository<HouseComplexAreaStatJpo, HouseComplexAreaStatId> {
	/**
	 * 단지들의 통계 행(B59·B84 모두). 비교표는 단지 3개 이하라 최대 6행이다. 호출하는 쪽이 빈 컬렉션이면 부르지 않는다.
	 */
	List<HouseComplexAreaStatJpo> findByComplexIdIn(Collection<String> complexIds);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_complex_area_stat", nativeQuery = true)
	int deleteAllStats();

	/**
	 * 거래에서 (단지, 면적 구간)별 통계를 한 문장으로 만든다. 안쪽 SELECT가 창별 평균·건수를 모으고,
	 * 바깥 SELECT가 파생값(최근 실거래가, 1·3년 변동)을 계산한다.
	 * - valid = 해제·원천 삭제·이상치가 아닌 거래. volume_3m만 이상치를 포함한다.
	 * - avg는 만원 정수로 반올림(numeric ROUND 후 bigint 캐스트). 변동률은 반올림된 avg로 계산한다.
	 * - 변동은 두 창 모두 2건 이상일 때만 낸다.
	 * 사람이 검증할 지점: 날짜 경계(왼쪽 열림, 오른쪽 닫힘), bigint/int 대입 캐스트, 반올림 값으로 계산한 pct,
	 * 6개월 대체 시 recent_* 값.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_complex_area_stat (complex_id, area_bucket, avg_area_12m, "
		+ "avg_3m, cnt_3m, avg_6m, cnt_6m, avg_12m, cnt_12m, "
		+ "avg_3m_1y_ago, cnt_3m_1y_ago, avg_3m_3y_ago, cnt_3m_3y_ago, volume_3m, "
		+ "recent_price_manwon, recent_window_months, recent_sample, "
		+ "change_1y_pct, change_1y_manwon, change_3y_pct, change_3y_manwon, computed_date) "
		+ "SELECT g.complex_id, g.area_bucket, g.avg_area_12m, "
		+ "g.avg_3m, g.cnt_3m, g.avg_6m, g.cnt_6m, g.avg_12m, g.cnt_12m, "
		+ "g.avg_3m_1y_ago, g.cnt_3m_1y_ago, g.avg_3m_3y_ago, g.cnt_3m_3y_ago, g.volume_3m, "
		+ "CASE WHEN g.cnt_3m > 0 THEN g.avg_3m WHEN g.cnt_6m > 0 THEN g.avg_6m END, "
		+ "CASE WHEN g.cnt_3m > 0 THEN 3 WHEN g.cnt_6m > 0 THEN 6 END, "
		+ "CASE WHEN g.cnt_3m > 0 THEN g.cnt_3m WHEN g.cnt_6m > 0 THEN g.cnt_6m ELSE 0 END, "
		+ "CASE WHEN g.cnt_3m >= 2 AND g.cnt_3m_1y_ago >= 2 AND g.avg_3m_1y_ago > 0 "
		+ "     THEN ROUND((g.avg_3m - g.avg_3m_1y_ago) * 100.0 / g.avg_3m_1y_ago, 2) END, "
		+ "CASE WHEN g.cnt_3m >= 2 AND g.cnt_3m_1y_ago >= 2 THEN g.avg_3m - g.avg_3m_1y_ago END, "
		+ "CASE WHEN g.cnt_3m >= 2 AND g.cnt_3m_3y_ago >= 2 AND g.avg_3m_3y_ago > 0 "
		+ "     THEN ROUND((g.avg_3m - g.avg_3m_3y_ago) * 100.0 / g.avg_3m_3y_ago, 2) END, "
		+ "CASE WHEN g.cnt_3m >= 2 AND g.cnt_3m_3y_ago >= 2 THEN g.avg_3m - g.avg_3m_3y_ago END, "
		+ "CAST(:today AS date) "
		+ "FROM ( "
		+ " SELECT t.complex_id AS complex_id, t.area_bucket AS area_bucket, "
		+ "  ROUND(AVG(t.exclusive_area) FILTER (WHERE t.valid AND t.deal_date > CAST(:w12mStart AS date)), 2) AS avg_area_12m, "
		+ "  CAST(ROUND(AVG(t.amount_manwon) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3mStart AS date))) AS bigint) AS avg_3m, "
		+ "  COUNT(*) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3mStart AS date)) AS cnt_3m, "
		+ "  CAST(ROUND(AVG(t.amount_manwon) FILTER (WHERE t.valid AND t.deal_date > CAST(:w6mStart AS date))) AS bigint) AS avg_6m, "
		+ "  COUNT(*) FILTER (WHERE t.valid AND t.deal_date > CAST(:w6mStart AS date)) AS cnt_6m, "
		+ "  CAST(ROUND(AVG(t.amount_manwon) FILTER (WHERE t.valid AND t.deal_date > CAST(:w12mStart AS date))) AS bigint) AS avg_12m, "
		+ "  COUNT(*) FILTER (WHERE t.valid AND t.deal_date > CAST(:w12mStart AS date)) AS cnt_12m, "
		+ "  CAST(ROUND(AVG(t.amount_manwon) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3m1yStart AS date) "
		+ "    AND t.deal_date <= CAST(:w3m1yEnd AS date))) AS bigint) AS avg_3m_1y_ago, "
		+ "  COUNT(*) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3m1yStart AS date) "
		+ "    AND t.deal_date <= CAST(:w3m1yEnd AS date)) AS cnt_3m_1y_ago, "
		+ "  CAST(ROUND(AVG(t.amount_manwon) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3m3yStart AS date) "
		+ "    AND t.deal_date <= CAST(:w3m3yEnd AS date))) AS bigint) AS avg_3m_3y_ago, "
		+ "  COUNT(*) FILTER (WHERE t.valid AND t.deal_date > CAST(:w3m3yStart AS date) "
		+ "    AND t.deal_date <= CAST(:w3m3yEnd AS date)) AS cnt_3m_3y_ago, "
		+ "  COUNT(*) FILTER (WHERE NOT t.cancelled AND NOT t.removed AND t.deal_date > CAST(:w3mStart AS date)) AS volume_3m "
		+ " FROM (SELECT x.complex_id, x.area_bucket, x.deal_date, x.exclusive_area, x.amount_manwon, x.cancelled, x.removed, "
		+ "        (NOT x.cancelled AND NOT x.removed AND NOT x.outlier) AS valid "
		+ "       FROM slcn.house_trade x "
		+ "       WHERE x.area_bucket IN ('B59', 'B84') "
		+ "         AND x.deal_date > CAST(:w3m3yStart AS date) AND x.deal_date <= CAST(:today AS date)) t "
		+ " GROUP BY t.complex_id, t.area_bucket "
		+ ") g",
		nativeQuery = true)
	int insertStats(@Param("today") LocalDate today, @Param("w3mStart") LocalDate w3mStart,
		@Param("w6mStart") LocalDate w6mStart, @Param("w12mStart") LocalDate w12mStart,
		@Param("w3m1yStart") LocalDate w3m1yStart, @Param("w3m1yEnd") LocalDate w3m1yEnd,
		@Param("w3m3yStart") LocalDate w3m3yStart, @Param("w3m3yEnd") LocalDate w3m3yEnd);
}
