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
public class TravelPhoto extends DomainEntity {
	private String travelId;
	private String travelDayId;
	private String travelPlaceId;
	private String photoFileId;
	private String caption;
	private int sortOrder;

	public TravelPhoto(String travelId, String travelDayId, String travelPlaceId, String photoFileId, String caption,
		int sortOrder) {
		super();
		this.travelId = travelId;
		this.travelDayId = travelDayId;
		this.travelPlaceId = travelPlaceId;
		this.photoFileId = photoFileId;
		this.caption = caption;
		this.sortOrder = sortOrder;
	}
}
