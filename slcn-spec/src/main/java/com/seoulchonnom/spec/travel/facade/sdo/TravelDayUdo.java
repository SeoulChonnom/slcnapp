package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelDayUdo {
	private String id;
	private String date;
	private String title;
	private String memo;
	private String coverPhotoId;
	private Integer sortOrder;
	private List<TravelPhotoCdo> photos;
	private List<TravelPlaceUdo> places;

	public TravelDayUdo(String title, String memo, String coverPhotoId, Integer sortOrder) {
		this.title = title;
		this.memo = memo;
		this.coverPhotoId = coverPhotoId;
		this.sortOrder = sortOrder;
	}
}
