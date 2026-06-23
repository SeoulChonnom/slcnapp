package com.seoulchonnom.spec.trip.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileReferenceSdo;

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
	private FileReferenceSdo logo;
	private FileReferenceSdo firstMap;
	private FileReferenceSdo secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
}
