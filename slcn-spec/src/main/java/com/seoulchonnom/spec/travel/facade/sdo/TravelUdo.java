package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelUdo {
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private List<String> tags;
	private Boolean confirmDeleteDays;
	private List<TravelDayUdo> travelDays;
	private TravelReviewUdo review;
	private List<FileBoxItemUdo> files;

	public TravelUdo(String title, String region, String startDate, String endDate, List<String> tags,
		Boolean confirmDeleteDays) {
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.tags = tags;
		this.confirmDeleteDays = confirmDeleteDays;
	}
}
