package com.seoulchonnom.spec.travel.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

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
public class TravelPlace implements JsonSerializable {
	private String placeKey;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private int sortOrder;
}
