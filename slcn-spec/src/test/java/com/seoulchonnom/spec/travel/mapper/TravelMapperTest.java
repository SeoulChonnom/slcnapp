package com.seoulchonnom.spec.travel.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;
import com.seoulchonnom.spec.travel.entity.TravelReview;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;

class TravelMapperTest {
	private final TravelMapper travelMapper = new TravelMapper();

	@Test
	void toTravelRdo_shouldCalculateNightsAndDays() {
		Travel travel = Travel.builder()
			.title("서울")
			.startDate(LocalDate.of(2026, 6, 1))
			.endDate(LocalDate.of(2026, 6, 3))
			.build();
		travel.setId("travel-1");

		TravelRdo rdo = travelMapper.toTravelRdo(travel);

		assertThat(rdo.getNights()).isEqualTo(2);
		assertThat(rdo.getDays()).isEqualTo(3);
		assertThat(rdo.getStartDate()).isEqualTo("2026-06-01");
	}

	@Test
	void toTravelDetailRdo_shouldMapChildren() {
		Travel travel = Travel.builder()
			.title("서울")
			.startDate(LocalDate.of(2026, 6, 1))
			.endDate(LocalDate.of(2026, 6, 2))
			.build();
		travel.setId("travel-1");
		TravelDay travelDay = TravelDay.builder()
			.travelId("travel-1")
			.date(LocalDate.of(2026, 6, 1))
			.dayNumber(1)
			.sortOrder(1)
			.build();
		travelDay.setId("day-1");
		TravelReview review = new TravelReview("travel-1", "좋았음");
		review.setId("review-1");

		TravelDetailRdo detailRdo = travelMapper.toTravelDetailRdo(travel, List.of(travelDay), List.of(), List.of(),
			List.of(), review);

		assertThat(detailRdo.getTravelDays()).hasSize(1);
		assertThat(detailRdo.getReview().getContent()).isEqualTo("좋았음");
	}
}
