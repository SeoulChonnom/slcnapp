package com.seoulchonnom.spec.travel.facade.sdo;

import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlaceCdo {
	private String travelDayId;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private String coverPhotoId;
	private java.util.List<String> photoFileIds;
	private Integer sortOrder;
}
