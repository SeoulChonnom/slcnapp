package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WGS84 좌표(도 단위).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GeoPointRdo {
	private double latitude;
	private double longitude;
}
