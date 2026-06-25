package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.List;

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
	private String coverPhotoId;
	private List<String> tags;
	private Boolean confirmDeleteDays;
	private List<TravelDayUdo> travelDays;
	private List<TravelPhotoCdo> photos;
	private TravelReviewUdo review;

	public TravelUdo(String title, String region, String startDate, String endDate, String coverPhotoId,
		List<String> tags, Boolean confirmDeleteDays) {
		this.title = title;
		this.region = region;
		this.startDate = startDate;
		this.endDate = endDate;
		this.coverPhotoId = coverPhotoId;
		this.tags = tags;
		this.confirmDeleteDays = confirmDeleteDays;
	}
}
