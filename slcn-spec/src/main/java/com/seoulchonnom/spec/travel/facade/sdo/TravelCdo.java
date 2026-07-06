package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelCdo {
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private List<String> tags;
	private List<TravelDayUdo> travelDays;
	private TravelReviewUdo review;
	private List<FileBoxItemCdo> files;

	public TravelCdo(String title, String region, String startDate, String endDate, List<String> tags) {
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.tags = tags;
	}
}
