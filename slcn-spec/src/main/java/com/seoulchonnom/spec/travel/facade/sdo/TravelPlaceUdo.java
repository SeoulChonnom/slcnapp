package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlaceUdo {
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private Integer sortOrder;
	private String coverPhotoId;
	private List<TravelPhotoCdo> photos;
}
