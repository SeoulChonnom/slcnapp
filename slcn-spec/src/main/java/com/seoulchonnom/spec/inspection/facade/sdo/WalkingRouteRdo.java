package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 임장 도보 경로. 저장하지 않고 요청 때마다 계산한다. 단위는 거리 m, 시간 초다.
 * 지점이 2개 미만이면 0과 빈 목록이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalkingRouteRdo {
	private int totalDistance;
	private int totalTime;
	private List<WalkingRouteStopRdo> stops;
	private List<WalkingRouteLegRdo> legs;
}
