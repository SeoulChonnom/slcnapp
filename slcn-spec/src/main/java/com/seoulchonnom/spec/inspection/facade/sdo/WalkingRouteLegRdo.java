package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인접한 두 지점 사이의 구간.
 * path의 각 원소는 [longitude, latitude] 순서(GeoJSON과 같다)이므로 위도 경도 순으로 읽으면 안 된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalkingRouteLegRdo {
	private int fromStopIndex;
	private int toStopIndex;
	private int distance;
	private int time;
	private List<List<Double>> path;
}
