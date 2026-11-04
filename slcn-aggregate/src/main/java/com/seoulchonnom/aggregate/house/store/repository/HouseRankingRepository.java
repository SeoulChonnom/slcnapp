package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseRankingId;
import com.seoulchonnom.aggregate.house.store.jpo.HouseRankingJpo;

/**
 * 쓰기는 네이티브 DELETE + INSERT뿐이다. 읽기는 랭킹 입력(통계 + 단지)과 랭킹 조회(단지·통계 LEFT JOIN).
 */
@Repository
public interface HouseRankingRepository extends JpaRepository<HouseRankingJpo, HouseRankingId> {
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_ranking", nativeQuery = true)
	int deleteAllRows();

	/**
	 * 랭킹 행을 같은 길이의 쉼표 문자열 11개로 받아 unnest로 한꺼번에 넣는다. 값에는 쉼표가 없다
	 * (타입·구간·정수·단지 ID·소수·날짜·배지 이름). null은 빈 문자열로 보내고 NULLIF로 되돌린다.
	 * 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: 다중 인자 unnest 열 이름, 빈 문자열 → null 변환(complex_id, value_num, change_manwon, badge),
	 * numeric(12,2) 캐스트, 날짜 캐스트, 복합 PK 충돌이 없는지.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_ranking (ranking_type, area_bucket, rank_no, lawd_cd, complex_id, "
		+ "value_num, change_manwon, sample_count, badge, period_from, period_to, computed_date) "
		+ "SELECT t.ranking_type, t.area_bucket, CAST(t.rank_no AS integer), t.lawd_cd, NULLIF(t.complex_id, ''), "
		+ "CAST(NULLIF(t.value_num, '') AS numeric(12,2)), CAST(NULLIF(t.change_manwon, '') AS bigint), "
		+ "CAST(t.sample_count AS integer), NULLIF(t.badge, ''), "
		+ "CAST(t.period_from AS date), CAST(t.period_to AS date), CAST(:computedDate AS date) "
		+ "FROM unnest(string_to_array(CAST(:types AS text), ','), "
		+ "            string_to_array(CAST(:areas AS text), ','), "
		+ "            string_to_array(CAST(:rankNos AS text), ','), "
		+ "            string_to_array(CAST(:lawdCds AS text), ','), "
		+ "            string_to_array(CAST(:complexIds AS text), ','), "
		+ "            string_to_array(CAST(:values AS text), ','), "
		+ "            string_to_array(CAST(:changes AS text), ','), "
		+ "            string_to_array(CAST(:samples AS text), ','), "
		+ "            string_to_array(CAST(:badges AS text), ','), "
		+ "            string_to_array(CAST(:periodFroms AS text), ','), "
		+ "            string_to_array(CAST(:periodTos AS text), ',')) "
		+ "     AS t(ranking_type, area_bucket, rank_no, lawd_cd, complex_id, value_num, change_manwon, "
		+ "          sample_count, badge, period_from, period_to)",
		nativeQuery = true)
	int insertAll(@Param("types") String types, @Param("areas") String areas, @Param("rankNos") String rankNos,
		@Param("lawdCds") String lawdCds, @Param("complexIds") String complexIds, @Param("values") String values,
		@Param("changes") String changes, @Param("samples") String samples, @Param("badges") String badges,
		@Param("periodFroms") String periodFroms, @Param("periodTos") String periodTos,
		@Param("computedDate") LocalDate computedDate);

	/**
	 * 랭킹 입력. 통계 행(B59·B84)마다 단지의 구 코드와 이름을 붙인다.
	 * 행은 [complex_id, area_bucket, lawd_cd, apt_name, cnt_12m, avg_12m, cnt_3m, cnt_3m_1y_ago, volume_3m,
	 * change_1y_pct, change_1y_manwon]다. 좌표가 없는 단지도 포함한다.
	 * 사람이 검증할 지점: JOIN으로 통계 행이 빠지지 않는지(통계 행 수와 같아야 함), numeric/bigint 반환 타입.
	 */
	@Query(value = "SELECT s.complex_id, s.area_bucket, c.lawd_cd, c.apt_name, s.cnt_12m, s.avg_12m, s.cnt_3m, "
		+ "s.cnt_3m_1y_ago, s.volume_3m, s.change_1y_pct, s.change_1y_manwon "
		+ "FROM slcn.house_complex_area_stat s JOIN slcn.house_complex c ON c.id = s.complex_id "
		+ "WHERE s.area_bucket IN ('B59', 'B84')",
		nativeQuery = true)
	List<Object[]> findRankingInputs();

	/**
	 * 랭킹 조회. 순위 순이고 단지·통계는 LEFT JOIN이라 "거래 없음" 행도 나온다.
	 * 행은 [rank_no, complex_id, apt_name, lawd_cd, latitude, longitude, build_year, avg_area_12m, households,
	 * value_num, change_manwon, sample_count, badge, period_from, period_to]다.
	 * build_year는 연식 규칙(사용승인연도, 없으면 실거래 건축년도)이며 HouseComplex.resolveBuildYear()와 같은 규칙이다.
	 * 사람이 검증할 지점: 날짜 컬럼이 LocalDate 또는 java.sql.Date로 오는지, NO_TRADE 행의 null 컬럼, 정렬.
	 */
	@Query(value = "SELECT r.rank_no, r.complex_id, c.apt_name, r.lawd_cd, c.latitude, c.longitude, "
		+ "COALESCE(CAST(EXTRACT(YEAR FROM c.approval_date) AS integer), c.build_year) AS build_year, "
		+ "s.avg_area_12m, c.households, r.value_num, r.change_manwon, r.sample_count, r.badge, "
		+ "r.period_from, r.period_to "
		+ "FROM slcn.house_ranking r "
		+ "LEFT JOIN slcn.house_complex c ON c.id = r.complex_id "
		+ "LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = r.complex_id AND s.area_bucket = r.area_bucket "
		+ "WHERE r.ranking_type = :type AND r.area_bucket = :area "
		+ "ORDER BY r.rank_no",
		nativeQuery = true)
	List<Object[]> findRankingRows(@Param("type") String type, @Param("area") String area);
}
