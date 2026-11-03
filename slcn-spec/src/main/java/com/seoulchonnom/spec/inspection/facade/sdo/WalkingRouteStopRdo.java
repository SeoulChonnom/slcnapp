package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경로의 한 지점. 화면 순서에서 연속한 같은 좌표의 매물은 한 지점으로 합쳐져 propertyIds에 모두 담긴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalkingRouteStopRdo {
	private int index;
	private List<String> propertyIds;
	private double latitude;
	private double longitude;
}
