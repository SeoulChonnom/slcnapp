package com.seoulchonnom.aggregate.inspection.geo;

/**
 * WGS84(EPSG:4326) 좌표. 도 단위다.
 */
public record GeoPoint(double latitude, double longitude) {
}
