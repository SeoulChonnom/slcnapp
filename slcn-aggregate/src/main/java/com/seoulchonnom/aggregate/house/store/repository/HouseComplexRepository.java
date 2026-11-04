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

	/**
	 * 지도 영역·구 단위 목록. 좌표가 보강된 단지만, 최근 실거래가 내림차순(가격 없는 단지는 뒤).
	 * 조건 없는 항목은 플래그(:bounds, :byDistrict, :hasPrice)를 false로 두고 기본값을 넘긴다(null 파라미터 금지).
	 * 행은 [id, apt_name, lawd_cd, umd_name, latitude, longitude, build_year, households,
	 * recent_price_manwon, recent_window_months, recent_sample]다.
	 * build_year는 연식 규칙(사용승인연도, 없으면 실거래 건축년도)이며 HouseComplex.resolveBuildYear()와 같은 규칙이다.
	 * 사람이 검증할 지점: 플래그 OR 조건의 타입 추론, LEFT JOIN으로 통계 없는 단지 포함, 가격 양 끝 포함, 정렬.
	 */
	@Query(value = "SELECT c.id, c.apt_name, c.lawd_cd, c.umd_name, c.latitude, c.longitude, "
		+ "COALESCE(CAST(EXTRACT(YEAR FROM c.approval_date) AS integer), c.build_year) AS build_year, c.households, "
		+ "s.recent_price_manwon, s.recent_window_months, s.recent_sample "
		+ "FROM slcn.house_complex c "
		+ "LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area "
		+ "WHERE c.geocode_status = 'DONE' "
		+ "AND (:bounds = false OR (c.latitude BETWEEN :swLat AND :neLat AND c.longitude BETWEEN :swLng AND :neLng)) "
		+ "AND (:byDistrict = false OR c.lawd_cd = :lawdCd) "
		+ "AND (:hasPrice = false OR (s.recent_price_manwon IS NOT NULL "
		+ "     AND s.recent_price_manwon >= :minPrice AND s.recent_price_manwon <= :maxPrice)) "
		+ "ORDER BY s.recent_price_manwon DESC NULLS LAST, c.apt_name, c.id "
		+ "LIMIT :limit",
		nativeQuery = true)
	List<Object[]> findMapItems(@Param("area") String area, @Param("bounds") boolean bounds,
		@Param("swLat") double swLat, @Param("swLng") double swLng, @Param("neLat") double neLat,
		@Param("neLng") double neLng, @Param("byDistrict") boolean byDistrict, @Param("lawdCd") String lawdCd,
		@Param("hasPrice") boolean hasPrice, @Param("minPrice") long minPrice, @Param("maxPrice") long maxPrice,
		@Param("limit") int limit);

	/**
	 * findMapItems와 같은 조건의 전체 건수(LIMIT 없음).
	 */
	@Query(value = "SELECT COUNT(*) FROM slcn.house_complex c "
		+ "LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area "
		+ "WHERE c.geocode_status = 'DONE' "
		+ "AND (:bounds = false OR (c.latitude BETWEEN :swLat AND :neLat AND c.longitude BETWEEN :swLng AND :neLng)) "
		+ "AND (:byDistrict = false OR c.lawd_cd = :lawdCd) "
		+ "AND (:hasPrice = false OR (s.recent_price_manwon IS NOT NULL "
		+ "     AND s.recent_price_manwon >= :minPrice AND s.recent_price_manwon <= :maxPrice))",
		nativeQuery = true)
	long countMapItems(@Param("area") String area, @Param("bounds") boolean bounds, @Param("swLat") double swLat,
		@Param("swLng") double swLng, @Param("neLat") double neLat, @Param("neLng") double neLng,
		@Param("byDistrict") boolean byDistrict, @Param("lawdCd") String lawdCd,
		@Param("hasPrice") boolean hasPrice, @Param("minPrice") long minPrice, @Param("maxPrice") long maxPrice);

	/**
	 * 구별 후보 수. 행은 [lawd_cd, cnt]다. 좌표 있는 단지가 없는 구는 행이 없다(Java에서 0으로 채운다).
	 * 사람이 검증할 지점: FILTER 안의 플래그 조건, 통계 없는 단지는 가격 필터가 켜지면 빠지는지.
	 */
	@Query(value = "SELECT c.lawd_cd, "
		+ "COUNT(*) FILTER (WHERE :hasPrice = false OR s.recent_price_manwon BETWEEN :minPrice AND :maxPrice) AS cnt "
		+ "FROM slcn.house_complex c "
		+ "LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area "
		+ "WHERE c.geocode_status = 'DONE' "
		+ "GROUP BY c.lawd_cd",
		nativeQuery = true)
	List<Object[]> countByDistrict(@Param("area") String area, @Param("hasPrice") boolean hasPrice,
		@Param("minPrice") long minPrice, @Param("maxPrice") long maxPrice);

	/**
	 * 동별 후보 수와 중심. 중심은 필터와 무관하게 좌표 있는 전체 단지의 평균이고(COUNT에만 FILTER), 중심이 영역 안인 동만 낸다.
	 * 행은 [lawd_cd, umd_cd, umd_name, center_lat, center_lng, cnt]다. 후보 수 내림차순, 그다음 동 이름.
	 * 사람이 검증할 지점: HAVING 경계, 필터를 바꿔도 center가 그대로인지, umd_cd가 null인 단지(Java에서 건너뜀).
	 */
	@Query(value = "SELECT c.lawd_cd, c.umd_cd, MAX(c.umd_name) AS umd_name, "
		+ "AVG(c.latitude) AS center_lat, AVG(c.longitude) AS center_lng, "
		+ "COUNT(*) FILTER (WHERE :hasPrice = false OR s.recent_price_manwon BETWEEN :minPrice AND :maxPrice) AS cnt "
		+ "FROM slcn.house_complex c "
		+ "LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area "
		+ "WHERE c.geocode_status = 'DONE' "
		+ "GROUP BY c.lawd_cd, c.umd_cd "
		+ "HAVING AVG(c.latitude) BETWEEN :swLat AND :neLat AND AVG(c.longitude) BETWEEN :swLng AND :neLng "
		+ "ORDER BY cnt DESC, umd_name, c.lawd_cd, c.umd_cd",
		nativeQuery = true)
	List<Object[]> summarizeByDong(@Param("area") String area, @Param("hasPrice") boolean hasPrice,
		@Param("minPrice") long minPrice, @Param("maxPrice") long maxPrice, @Param("swLat") double swLat,
		@Param("swLng") double swLng, @Param("neLat") double neLat, @Param("neLng") double neLng);

	/**
	 * 단지명 검색. pattern은 호출하는 쪽이 만든 LIKE 패턴(소문자, 공백 제거, 백슬래시·%·_ 이스케이프, 앞뒤 %)이다.
	 * 좌표가 없는 단지도 나온다(비교표는 좌표 없이도 의미가 있다). 이스케이프 문자는 PostgreSQL 기본값인 백슬래시다.
	 * 사람이 검증할 지점: '%'만 넣어도 전체가 나오지 않는지, '_' 이스케이프, 영문 대소문자 무시, 공백이 낀 단지명,
	 * c.*가 HouseComplexJpo로 매핑되는지, 정렬.
	 */
	@Query(value = "SELECT c.* FROM slcn.house_complex c "
		+ "WHERE LOWER(REPLACE(c.apt_name, ' ', '')) LIKE :pattern "
		+ "ORDER BY c.apt_name, c.id "
		+ "LIMIT :size",
		nativeQuery = true)
	List<HouseComplexJpo> searchByName(@Param("pattern") String pattern, @Param("size") int size);

	/**
	 * 좌표가 확정된 단지의 위치. 최근접 학교 계산 입력이다. 행은 [id, latitude, longitude]다.
	 * 사람이 검증할 지점: geocode_status = 'DONE'이면 좌표가 반드시 있는지, 단지 수천 행을 한 번에 읽는 시간.
	 */
	@Query(value = "SELECT c.id, c.latitude, c.longitude FROM slcn.house_complex c "
		+ "WHERE c.geocode_status = 'DONE' AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL "
		+ "ORDER BY c.id",
		nativeQuery = true)
	List<Object[]> findGeocodedPoints();
}
