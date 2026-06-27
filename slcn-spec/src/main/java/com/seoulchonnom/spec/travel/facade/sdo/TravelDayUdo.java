package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelDayUdo {
	private String date;
	private String title;
	private String memo;
	private Integer sortOrder;
	private List<TravelPlaceUdo> places;

	public TravelDayUdo(String title, String memo, Integer sortOrder) {
		this.title = title;
		this.memo = memo;
		this.sortOrder = sortOrder;
	}
}
