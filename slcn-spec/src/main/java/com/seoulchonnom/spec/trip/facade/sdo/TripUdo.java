package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripUdo {
	private String date;
	private String type;
	private String name;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	@Valid
	private QuizCdo quiz;
	@Valid
	private List<FileBoxItemUdo> files;
}
