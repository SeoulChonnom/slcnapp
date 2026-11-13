package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {
	@Test
	void haversineMeters_shouldBeOneDegreeOfArcAlongAMeridian() {
		// 위도 1도 = R x pi/180 = 111,195.08m (허용 오차 1m)
		assertThat(GeoDistance.haversineMeters(0, 0, 1, 0)).isCloseTo(111_195.08, within(1.0));
		assertThat(GeoDistance.haversineMeters(37.0, 127.0, 38.0, 127.0)).isCloseTo(111_195.08, within(1.0));
	}

	@Test
	void haversineMeters_shouldMatchKnownDistanceBetweenSeoulAndBusan() {
		// 서울시청 -> 부산시청 약 325.1km
		assertThat(GeoDistance.haversineMeters(37.5665, 126.9780, 35.1796, 129.0756)).isCloseTo(325_111.71, within(1.0));
	}

	@Test
	void haversineMeters_shouldShrinkLongitudeDegreeByCosOfLatitude() {
		assertThat(GeoDistance.haversineMeters(37.5, 127.0, 37.5, 127.001)).isCloseTo(88.22, within(1.0));
	}

	@Test
	void haversineMeters_shouldBeZeroForSamePointAndSymmetric() {
		assertThat(GeoDistance.haversineMeters(37.5, 127.0, 37.5, 127.0)).isZero();
		assertThat(GeoDistance.haversineMeters(37.5, 127.0, 37.6, 127.1))
			.isEqualTo(GeoDistance.haversineMeters(37.6, 127.1, 37.5, 127.0));
	}
}
