package com.seoulchonnom.spec.inspection.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 임장을 다니는 지역/생활권. 개별 단지가 아니다.
 * 단지명은 ViewedProperty.complexName으로 내려간다.
 * visitCount, lastVisitedAt은 저장하지 않고 조회 시 집계한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InspectionArea extends DomainEntity {
	private String name;
	private String description;

	public InspectionArea(String id, String name, String description) {
		super(id);
		this.name = name;
		this.description = description;
	}

	public void update(String name, String description) {
		this.name = name;
		this.description = description;
		this.modifiedTime = System.currentTimeMillis();
	}

}
