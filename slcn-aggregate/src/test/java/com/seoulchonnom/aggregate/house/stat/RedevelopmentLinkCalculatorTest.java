package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.stat.RedevelopmentLinkCalculator.Link;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;

class RedevelopmentLinkCalculatorTest {
	private static final double LAT = 37.5;
	private static final double LNG = 127.0;
	private static final ComplexPointPdo COMPLEX = new ComplexPointPdo("C1", LAT, LNG);

	private static HouseRedevelopment at(String id, double latitude, double longitude) {
		HouseRedevelopment redevelopment = HouseRedevelopment.builder().sourceKey(id).latitude(latitude)
			.longitude(longitude).build();
		redevelopment.setId(id);
		return redevelopment;
	}

	/** 정북쪽으로 meters만큼 떨어진 위도. 위도 1도 = 지구 반지름 x 라디안이다. */
	private static double northOf(double meters) {
		return LAT + Math.toDegrees(meters / GeoDistance.EARTH_RADIUS_METERS);
	}

	@Test
	void calculate_shouldLinkRedevelopmentsWithinRadiusWithRoundedDistance() {
		List<Link> links = RedevelopmentLinkCalculator.calculate(List.of(COMPLEX),
			List.of(at("R1", northOf(320.4), LNG)));

		assertThat(links).containsExactly(new Link("C1", "R1", 320));
	}

	@Test
	void calculate_shouldIncludeTheFiveHundredMeterBoundary() {
		double latitude = northOf(499.99);
		double meters = GeoDistance.haversineMeters(LAT, LNG, latitude, LNG);
		assertThat(meters).isLessThanOrEqualTo(500.0);

		List<Link> links = RedevelopmentLinkCalculator.calculate(List.of(COMPLEX), List.of(at("R1", latitude, LNG)));

		assertThat(links).containsExactly(new Link("C1", "R1", 500));
	}

	@Test
	void calculate_shouldExcludeJustBeyondFiveHundredMetersEvenIfRoundedDistanceIsFiveHundred() {
		List<Link> links = RedevelopmentLinkCalculator.calculate(List.of(COMPLEX),
			List.of(at("R1", northOf(500.4), LNG), at("R2", northOf(501), LNG)));

		assertThat(links).isEmpty();
	}

	@Test
	void calculate_shouldSkipRedevelopmentsWithoutCoordinatesAndHandleEmptyInputs() {
		HouseRedevelopment noPoint = HouseRedevelopment.builder().sourceKey("R0").build();

		assertThat(RedevelopmentLinkCalculator.calculate(List.of(COMPLEX), List.of(noPoint))).isEmpty();
		assertThat(RedevelopmentLinkCalculator.calculate(List.of(), List.of(at("R1", LAT, LNG)))).isEmpty();
	}
}
