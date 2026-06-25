package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelRdo {
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
	private java.util.List<TravelTagRdo> tags = new java.util.ArrayList<>();
}
