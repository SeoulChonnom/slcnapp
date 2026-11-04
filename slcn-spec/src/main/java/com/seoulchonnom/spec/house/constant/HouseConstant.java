package com.seoulchonnom.spec.house.constant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class HouseConstant {
	public static final String HOUSE_COMPLEX_NOT_FOUND_ERROR_MESSAGE = "단지 정보를 찾을 수 없습니다.";
	public static final String HOUSE_BUDGET_POLICY_NOT_FOUND_ERROR_MESSAGE = "예산 계산 규칙을 불러올 수 없습니다.";

	// 면적 구간 기본 경계(전용면적 ㎡). 59형: [55, 65), 84형: [80, 90)
	public static final BigDecimal AREA_B59_FROM = new BigDecimal("55.00");
	public static final BigDecimal AREA_B59_TO = new BigDecimal("65.00");
	public static final BigDecimal AREA_B84_FROM = new BigDecimal("80.00");
	public static final BigDecimal AREA_B84_TO = new BigDecimal("90.00");

	/**
	 * 서울 25개 구. 중심 좌표는 구청 좌표(행안부 좌표제공 API로 조회)다. lawdCd 오름차순.
	 */
	public static final List<District> DISTRICTS = List.of(
		new District("11110", "종로구", 37.573505, 126.978988),
		new District("11140", "중구", 37.563881, 126.998180),
		new District("11170", "용산구", 37.532479, 126.990482),
		new District("11200", "성동구", 37.563423, 127.036965),
		new District("11215", "광진구", 37.538313, 127.083109),
		new District("11230", "동대문구", 37.574392, 127.039897),
		new District("11260", "중랑구", 37.606262, 127.093049),
		new District("11290", "성북구", 37.589440, 127.016770),
		new District("11305", "강북구", 37.639339, 127.025656),
		new District("11320", "도봉구", 37.668679, 127.047066),
		new District("11350", "노원구", 37.653618, 127.056703),
		new District("11380", "은평구", 37.602176, 126.929338),
		new District("11410", "서대문구", 37.579207, 126.936285),
		new District("11440", "마포구", 37.565847, 126.901208),
		new District("11470", "양천구", 37.517108, 126.866502),
		new District("11500", "강서구", 37.551112, 126.849302),
		new District("11530", "구로구", 37.495064, 126.888046),
		new District("11545", "금천구", 37.456441, 126.895507),
		new District("11560", "영등포구", 37.526046, 126.896525),
		new District("11590", "동작구", 37.512367, 126.939872),
		new District("11620", "관악구", 37.478166, 126.951628),
		new District("11650", "서초구", 37.484217, 127.033357),
		new District("11680", "강남구", 37.518131, 127.047301),
		new District("11710", "송파구", 37.514811, 127.106667),
		new District("11740", "강동구", 37.529775, 127.123513));

	private HouseConstant() {
	}

	public static boolean isDistrict(String lawdCd) {
		return findDistrict(lawdCd).isPresent();
	}

	public static Optional<District> findDistrict(String lawdCd) {
		return DISTRICTS.stream().filter(district -> district.lawdCd().equals(lawdCd)).findFirst();
	}

	/**
	 * 구 이름("강남구")으로 법정동 시군구 코드를 찾는다. 서울 25개 구가 아니면 null이다.
	 */
	public static String lawdCdOf(String districtName) {
		return DISTRICTS.stream().filter(district -> district.name().equals(districtName)).findFirst()
			.map(District::lawdCd).orElse(null);
	}

	/**
	 * 서울 25개 구가 아니면 null이다.
	 */
	public static String districtName(String lawdCd) {
		return findDistrict(lawdCd).map(District::name).orElse(null);
	}

	public record District(String lawdCd, String name, double centerLat, double centerLng) {
	}
}
