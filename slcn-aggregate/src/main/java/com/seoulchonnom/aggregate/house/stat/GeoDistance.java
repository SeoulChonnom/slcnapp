package com.seoulchonnom.aggregate.house.stat;

/**
 * 두 WGS84 좌표 사이의 직선(대원) 거리. 학교·정비사업까지의 거리는 도보 경로가 아니라 직선거리다.
 */
public final class GeoDistance {
	/** 평균 지구 반지름(m) */
	static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private GeoDistance() {
	}

	public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
		double phi1 = Math.toRadians(lat1);
		double phi2 = Math.toRadians(lat2);
		double deltaPhi = Math.toRadians(lat2 - lat1);
		double deltaLambda = Math.toRadians(lng2 - lng1);
		double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
			+ Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}
}
