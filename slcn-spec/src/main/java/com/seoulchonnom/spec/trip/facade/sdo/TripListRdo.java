package com.seoulchonnom.spec.trip.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileReferenceSdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripListRdo {
	private String id;
	private String date;
	private String type;
	private String name;
	private FileReferenceSdo logo;
}
