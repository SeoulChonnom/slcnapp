package com.seoulchonnom.spec.house.entity;

import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정비사업 참조 데이터. 서울시 도시정비사업 통계(OA-22856) 엑셀을 관리자가 CSV로 올려 source_key(=CODE) 기준으로 통째 교체한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseRedevelopment extends DomainEntity {
	/** 원천 데이터의 구역 코드(CODE) */
	private String sourceKey;
	/** 구역명 */
	private String name;
	/** 시군구 코드. 자치구 이름에서 정해진다. */
	private String lawdCd;
	private String jibunAddress;
	private String roadAddress;
	/** 좌표 검색에 먼저 쓰는 주소. 관리자가 직접 적은 값이다(선택). */
	private String geocodeQuery;
	/** 표준 표기 사업유형(주택정비형 재개발 등) */
	private String projectType;
	/** 추진단계(관리처분 등) */
	private String stage;
	private Double latitude;
	private Double longitude;
	@Builder.Default
	private GeocodeStatus geocodeStatus = GeocodeStatus.PENDING;
	private int geocodeAttempts;
	private String sourceUrl;
	private LocalDate syncedDate;
}
