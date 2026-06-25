package com.seoulchonnom.spec.travel.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelDetailRdo {
	private String id;
	private String travelId;
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private String coverPhotoId;
	private String oneLineReview;
	private int nights;
	private int days;
	private List<TravelDayRdo> travelDays = new ArrayList<>();
	private List<TravelPlaceRdo> places = new ArrayList<>();
	private List<TravelPhotoRdo> photos = new ArrayList<>();
	private List<TravelTagRdo> tags = new ArrayList<>();
	private TravelReviewRdo review;
}
