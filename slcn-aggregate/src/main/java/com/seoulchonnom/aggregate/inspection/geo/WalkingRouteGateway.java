package com.seoulchonnom.aggregate.inspection.geo;

import java.util.List;

import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;

/**
 * 도보 경로 조회 포트. 구현체가 어떤 API를 쓰는지는 도메인이 모른다.
 */
public interface WalkingRouteGateway {
	/**
	 * @param points 출발, 경유(0~5개), 도착 순서의 2~7개 지점
	 * @return 인접한 두 지점마다 구간 하나씩, 지점 수 - 1개의 구간
	 * @throws WalkingRouteException 설정이 없거나, 한도를 넘었거나, 호출/응답 해석에 실패했을 때
	 */
	WalkingRouteSegment route(List<GeoPoint> points);
}
