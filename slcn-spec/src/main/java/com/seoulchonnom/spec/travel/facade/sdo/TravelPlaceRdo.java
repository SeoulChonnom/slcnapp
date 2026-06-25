package com.seoulchonnom.spec.travel.facade.sdo;

import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelPlaceRdo {
	private String id;
	private String travelId;
	private String travelDayId;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private String coverPhotoId;
	private int sortOrder;
	private java.util.List<TravelPhotoRdo> photos = new java.util.ArrayList<>();
}
