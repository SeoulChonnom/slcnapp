package com.seoulchonnom.aggregate.inspection.geo;

import java.util.List;

import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;

/**
 * 카카오 REST 키가 없는 환경(개발·테스트)의 대역. 호출하면 항상 503이다.
 */
public class DisabledWalkingRouteGateway implements WalkingRouteGateway {
	@Override
	public WalkingRouteSegment route(List<GeoPoint> points) {
		throw WalkingRouteException.notConfigured();
	}
}
