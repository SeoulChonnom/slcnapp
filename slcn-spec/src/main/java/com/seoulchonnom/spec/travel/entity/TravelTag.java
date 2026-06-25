package com.seoulchonnom.spec.travel.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@Getter
@Setter
public class TravelTag extends DomainEntity {
	private String travelId;
	private String name;
	private int sortOrder;

	public TravelTag(String travelId, String name) {
		super();
		this.travelId = travelId;
		this.name = name;
	}

	public TravelTag(String travelId, String name, int sortOrder) {
		super();
		this.travelId = travelId;
		this.name = name;
		this.sortOrder = sortOrder;
	}
}
