package com.seoulchonnom.spec.travel.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TravelReviewRdo {
	private String oneLineSummary;
	private String goodPoint;
	private String badPoint;
	private String revisitPlace;
	private String finalReview;
}
