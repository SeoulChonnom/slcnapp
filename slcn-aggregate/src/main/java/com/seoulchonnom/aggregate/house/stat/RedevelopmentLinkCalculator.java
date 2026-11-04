package com.seoulchonnom.aggregate.house.stat;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;

/**
 * 좌표가 있는 단지 x 좌표가 확정된 정비사업 구역에서 직선거리 반경 안의 쌍을 찾는다. 단순 전수 비교다(단지 수천 x 구역 수백).
 */
public final class RedevelopmentLinkCalculator {
	/** 단지에서 이 거리(m) 이내(경계 포함)의 정비사업을 연결한다. */
	public static final double RADIUS_METERS = 500;

	private RedevelopmentLinkCalculator() {
	}

	/**
	 * @param distanceM 직선거리(m), 반올림한 정수
	 */
	public record Link(String complexId, String redevelopmentId, int distanceM) {
	}

	/**
	 * 반경 판정은 반올림 전 거리로 한다. 500.4m는 500으로 보이더라도 반경 밖이다.
	 */
	public static List<Link> calculate(List<ComplexPointPdo> complexes, List<HouseRedevelopment> redevelopments) {
		List<Link> links = new ArrayList<>();
		for (ComplexPointPdo complex : complexes) {
			for (HouseRedevelopment redevelopment : redevelopments) {
				if (redevelopment.getLatitude() == null || redevelopment.getLongitude() == null) {
					continue;
				}
				double meters = GeoDistance.haversineMeters(complex.latitude(), complex.longitude(),
					redevelopment.getLatitude(), redevelopment.getLongitude());
				if (meters <= RADIUS_METERS) {
					links.add(new Link(complex.complexId(), redevelopment.getId(), (int)Math.round(meters)));
				}
			}
		}
		return links;
	}
}
