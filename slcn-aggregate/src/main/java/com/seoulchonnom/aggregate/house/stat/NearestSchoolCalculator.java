package com.seoulchonnom.aggregate.house.stat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseSchool;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

/**
 * 단지마다 학교급별(초·중·고) 가장 가까운 학교를 찾는다. 단지 수천 x 서울 학교 약 1,300을 모두 비교하는 단순 탐색이다.
 * 학교급 안에 학교가 하나도 없으면 그 학교급의 결과 행은 만들지 않는다.
 */
public final class NearestSchoolCalculator {
	private NearestSchoolCalculator() {
	}

	/**
	 * @param distanceM 직선거리(m), 반올림한 정수
	 */
	public record Nearest(String complexId, SchoolLevel level, String schoolId, int distanceM) {
	}

	/**
	 * 거리가 같으면 school_code가 앞선 학교를 고른다. DB 조회 순서에 따라 결과가 흔들리지 않게 하려는 것이다.
	 */
	public static List<Nearest> calculate(List<ComplexPointPdo> complexes, List<HouseSchool> schools) {
		Map<SchoolLevel, List<HouseSchool>> byLevel = new EnumMap<>(SchoolLevel.class);
		for (HouseSchool school : schools) {
			byLevel.computeIfAbsent(school.getLevel(), level -> new ArrayList<>()).add(school);
		}
		List<Nearest> result = new ArrayList<>();
		for (ComplexPointPdo complex : complexes) {
			for (SchoolLevel level : SchoolLevel.values()) {
				HouseSchool best = null;
				double bestMeters = Double.MAX_VALUE;
				for (HouseSchool school : byLevel.getOrDefault(level, List.of())) {
					double meters = GeoDistance.haversineMeters(complex.latitude(), complex.longitude(),
						school.getLatitude(), school.getLongitude());
					if (meters < bestMeters
						|| (meters == bestMeters && school.getSchoolCode().compareTo(best.getSchoolCode()) < 0)) {
						best = school;
						bestMeters = meters;
					}
				}
				if (best != null) {
					result.add(new Nearest(complex.complexId(), level, best.getId(), (int)Math.round(bestMeters)));
				}
			}
		}
		return result;
	}
}
