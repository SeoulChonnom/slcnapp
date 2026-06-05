package com.seoulchonnom.spec.trip.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripDetailRdo {
	private String id;
	private String date;
	private String type;
	private String name;
	private String logo;
	private String firstMap;
	private String secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
}
