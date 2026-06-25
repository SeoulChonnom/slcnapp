package com.seoulchonnom.spec.travel.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TravelPlace extends DomainEntity {
	private String travelId;
	private String travelDayId;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private String coverPhotoId;
	private int sortOrder;

	public TravelPlace(String travelId, String travelDayId, String name, TravelPlaceCategory category, String description,
		String coverPhotoId, int sortOrder) {
		super();
		this.travelId = travelId;
		this.travelDayId = travelDayId;
		this.name = name;
		this.category = category;
		this.description = description;
		this.coverPhotoId = coverPhotoId;
		this.sortOrder = sortOrder;
	}

	public void update(String name, TravelPlaceCategory category, String description, String coverPhotoId,
		int sortOrder) {
		this.name = name;
		this.category = category;
		this.description = description;
		this.coverPhotoId = coverPhotoId;
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}
}
