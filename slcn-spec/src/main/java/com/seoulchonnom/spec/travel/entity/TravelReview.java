package com.seoulchonnom.spec.travel.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TravelReview extends DomainEntity {
	private String travelId;
	private String content;
	private String oneLineSummary;
	private String goodPoint;
	private String badPoint;
	private String revisitPlace;
	private String finalReview;

	public TravelReview(String travelId, String content) {
		super();
		this.travelId = travelId;
		this.content = content;
	}

	public void update(String content) {
		this.content = content;
		this.modifiedTime = System.currentTimeMillis();
	}

	public TravelReview(String travelId, String oneLineSummary, String goodPoint, String badPoint, String revisitPlace,
		String finalReview) {
		super();
		this.travelId = travelId;
		update(oneLineSummary, goodPoint, badPoint, revisitPlace, finalReview);
	}

	public void update(String oneLineSummary, String goodPoint, String badPoint, String revisitPlace,
		String finalReview) {
		this.oneLineSummary = oneLineSummary;
		this.goodPoint = goodPoint;
		this.badPoint = badPoint;
		this.revisitPlace = revisitPlace;
		this.finalReview = finalReview;
		this.content = finalReview;
		this.modifiedTime = System.currentTimeMillis();
	}
}
