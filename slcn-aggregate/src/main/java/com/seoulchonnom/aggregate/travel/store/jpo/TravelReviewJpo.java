package com.seoulchonnom.aggregate.travel.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travel_reviews", schema = "slcn", uniqueConstraints = {
	@UniqueConstraint(name = "uk_travel_reviews_travel", columnNames = "travel_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelReviewJpo extends DomainEntityJpo {
	private String travelId;
	private String content;
	private String oneLineSummary;
	private String goodPoint;
	private String badPoint;
	private String revisitPlace;
	private String finalReview;
}
