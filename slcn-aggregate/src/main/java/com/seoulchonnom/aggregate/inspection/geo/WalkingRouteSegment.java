package com.seoulchonnom.aggregate.inspection.geo;

import java.util.List;

/**
 * 한 번의 도보 경로 호출 결과. 거리는 m, 시간은 초다.
 */
public record WalkingRouteSegment(int totalDistance, int totalTime, List<Leg> legs) {
	/**
	 * @param fromIndex 이 구간이 시작하는 지점의 번호. route()에 넘긴 points 안에서의 상대 번호다.
	 * @param toIndex 이 구간이 끝나는 지점의 번호. 보통 fromIndex + 1이고, 상대가 구간을 나누지 않으면 여러 지점에 걸친다.
	 * @param path 구간을 따라가는 점들. GeoPoint는 위도·경도 순서의 이름 있는 값이라 순서를 헷갈리지 않는다.
	 */
	public record Leg(int fromIndex, int toIndex, int distance, int time, List<GeoPoint> path) {
	}
}
