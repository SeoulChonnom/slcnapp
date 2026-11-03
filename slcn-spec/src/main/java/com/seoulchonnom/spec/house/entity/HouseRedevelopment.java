package com.seoulchonnom.spec.house.entity;

import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정비사업 참조 데이터. 데이터셋이 아직 정해지지 않아 채우는 구현체가 없다(테이블만 먼저 만든다).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseRedevelopment extends DomainEntity {
	/** 원천 데이터에서 사업을 가리키는 키 */
	private String sourceKey;
	private String name;
	/** 재개발, 재건축 등 */
	private String projectType;
	/** 추진 단계(관리처분인가 등) */
	private String stage;
	private Double latitude;
	private Double longitude;
	private String sourceUrl;
	private LocalDate syncedDate;
}
