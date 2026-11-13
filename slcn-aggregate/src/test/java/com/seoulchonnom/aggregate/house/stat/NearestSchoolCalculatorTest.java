package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.stat.NearestSchoolCalculator.Nearest;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseSchool;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

class NearestSchoolCalculatorTest {
	private static HouseSchool school(String id, String code, SchoolLevel level, double lat, double lng) {
		HouseSchool school = HouseSchool.builder().schoolCode(code).name(code).level(level).latitude(lat)
			.longitude(lng).build();
		school.setId(id);
		return school;
	}

	private static final ComplexPointPdo COMPLEX = new ComplexPointPdo("C1", 37.5000, 127.0000);

	@Test
	void calculate_shouldPickNearestSchoolPerLevel() {
		List<HouseSchool> schools = List.of(
			school("s-e-far", "E2", SchoolLevel.E, 37.5100, 127.0000),
			school("s-e-near", "E1", SchoolLevel.E, 37.5010, 127.0000),
			school("s-m", "M1", SchoolLevel.M, 37.5000, 127.0030),
			school("s-h-far", "H2", SchoolLevel.H, 37.5000, 127.0200),
			school("s-h-near", "H1", SchoolLevel.H, 37.4950, 127.0000));

		List<Nearest> result = NearestSchoolCalculator.calculate(List.of(COMPLEX), schools);

		assertThat(result).extracting(Nearest::level).containsExactly(SchoolLevel.E, SchoolLevel.M, SchoolLevel.H);
		assertThat(result).extracting(Nearest::schoolId).containsExactly("s-e-near", "s-m", "s-h-near");
		// 위도 0.001도 = 111.2m
		assertThat(result.get(0).distanceM()).isEqualTo(111);
		assertThat(result.get(1).distanceM()).isEqualTo(Math.round(
			GeoDistance.haversineMeters(37.5, 127.0, 37.5, 127.003)));
		assertThat(result.get(2).distanceM()).isEqualTo(556);
	}

	@Test
	void calculate_shouldSkipLevelWithoutAnySchool() {
		List<HouseSchool> schools = List.of(school("s-e", "E1", SchoolLevel.E, 37.5010, 127.0000));

		List<Nearest> result = NearestSchoolCalculator.calculate(List.of(COMPLEX), schools);

		assertThat(result).extracting(Nearest::level).containsExactly(SchoolLevel.E);
	}

	@Test
	void calculate_shouldCalculateEachComplexIndependently() {
		ComplexPointPdo other = new ComplexPointPdo("C2", 37.5100, 127.0000);
		List<HouseSchool> schools = List.of(
			school("s-a", "E1", SchoolLevel.E, 37.5005, 127.0000),
			school("s-b", "E2", SchoolLevel.E, 37.5105, 127.0000));

		List<Nearest> result = NearestSchoolCalculator.calculate(List.of(COMPLEX, other), schools);

		assertThat(result).extracting(Nearest::complexId, Nearest::schoolId)
			.containsExactly(tuple("C1", "s-a"), tuple("C2", "s-b"));
	}

	@Test
	void calculate_shouldPickSmallerSchoolCodeWhenDistancesTie() {
		List<HouseSchool> schools = List.of(
			school("s-b", "E2", SchoolLevel.E, 37.5010, 127.0000),
			school("s-a", "E1", SchoolLevel.E, 37.5010, 127.0000));

		assertThat(NearestSchoolCalculator.calculate(List.of(COMPLEX), schools)).extracting(Nearest::schoolId)
			.containsExactly("s-a");
	}

	@Test
	void calculate_shouldReturnEmptyWhenNoComplexOrNoSchool() {
		assertThat(NearestSchoolCalculator.calculate(List.of(), List.of(school("s", "E1", SchoolLevel.E, 37.5, 127.0))))
			.isEmpty();
		assertThat(NearestSchoolCalculator.calculate(List.of(COMPLEX), List.of())).isEmpty();
	}
}
