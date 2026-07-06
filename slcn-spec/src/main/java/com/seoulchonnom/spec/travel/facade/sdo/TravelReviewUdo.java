package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelReviewUdo {
	private String oneLineSummary;
	private String goodPoint;
	private String badPoint;
	private String revisitPlace;
	private String finalReview;
}
