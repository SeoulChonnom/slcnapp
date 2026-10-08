package com.seoulchonnom.aggregate.inspection.geo;

/**
 * EPSG:5179(GRS80 UTM-K) 좌표. 단위는 미터이고 행안부 entX/entY에 해당한다.
 */
public record UtmkPoint(double x, double y) {
}
