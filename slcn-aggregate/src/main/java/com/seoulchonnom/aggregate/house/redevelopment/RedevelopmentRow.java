package com.seoulchonnom.aggregate.house.redevelopment;

/**
 * 검증을 통과한 CSV 한 행. 순번·공공/민간·일반/재촉은 저장하지 않으므로 담지 않는다.
 *
 * @param code         원천 구역 코드(CODE). source_key로 저장한다
 * @param projectType  표준 표기 사업유형
 * @param roadAddress  없으면 null
 * @param geocodeQuery 관리자가 적은 좌표 검색 주소. 없으면 null
 */
public record RedevelopmentRow(String code, String lawdCd, String name, String projectType, String stage,
							   String jibunAddress, String roadAddress, String geocodeQuery) {
}
