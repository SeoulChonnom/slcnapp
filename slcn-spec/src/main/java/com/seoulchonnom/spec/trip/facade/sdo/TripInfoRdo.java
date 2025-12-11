package com.seoulchonnom.spec.trip.facade.sdo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TripInfoRdo {
	private String date;
	private String map1;
	private String map2;
	private String button1;
	private String button2;
	private String drive;
}
