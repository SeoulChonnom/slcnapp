package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelDayRdo {
	private String id;
	private String travelId;
	private String date;
	private String title;
	private String memo;
	private String coverPhotoId;
	private int dayNumber;
	private int sortOrder;
	private java.util.List<TravelPlaceRdo> places = new java.util.ArrayList<>();
	private java.util.List<TravelPhotoRdo> photos = new java.util.ArrayList<>();
}
