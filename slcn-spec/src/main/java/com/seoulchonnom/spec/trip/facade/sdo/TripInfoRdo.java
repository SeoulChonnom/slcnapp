package com.seoulchonnom.spec.trip.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TripInfoRdo {
	private String date;
	private String firstMap;
	private String secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String drive;
}
