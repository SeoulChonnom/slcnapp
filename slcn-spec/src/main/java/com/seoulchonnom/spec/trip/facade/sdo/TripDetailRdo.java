package com.seoulchonnom.spec.trip.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileRefSdo;

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
	private FileRefSdo logo;
	private FileRefSdo firstMap;
	private FileRefSdo secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
}
