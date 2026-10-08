package com.seoulchonnom.aggregate.inspection.geo;

import java.util.List;

/**
 * 한 번의 도보 경로 호출 결과. 거리는 m, 시간은 초다.
 */
public record WalkingRouteSegment(int totalDistance, int totalTime, List<Leg> legs) {
	/**
	 * @param path 구간을 따라가는 점들. GeoPoint는 위도·경도 순서의 이름 있는 값이라 순서를 헷갈리지 않는다.
	 */
	public record Leg(int distance, int time, List<GeoPoint> path) {
	}
}
