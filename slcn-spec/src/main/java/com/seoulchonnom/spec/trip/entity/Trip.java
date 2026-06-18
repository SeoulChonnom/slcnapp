package com.seoulchonnom.spec.trip.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Trip extends DomainEntity {
	private String date;
	private String type;
	private String name;
	private FileReference logo;
	private FileReference firstMap;
	private FileReference secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private Quiz quiz;

	public Trip(TripCdo tripCdo, String id) {
		super(id);
		this.date = tripCdo.getDate();
		this.type = tripCdo.getType();
		this.name = tripCdo.getName();
		this.logo = new FileReference(tripCdo.getLogo().getType(), tripCdo.getLogo().getFilename());
		this.firstMap = new FileReference(tripCdo.getFirstMap().getType(), tripCdo.getFirstMap().getFilename());
		this.secondMap = tripCdo.getSecondMap() == null
			? null
			: new FileReference(tripCdo.getSecondMap().getType(), tripCdo.getSecondMap().getFilename());
		this.nextButtonText = tripCdo.getNextButtonText();
		this.previousButtonText = tripCdo.getPreviousButtonText();
		this.driveUrl = tripCdo.getDriveUrl();
		this.quiz = new Quiz(tripCdo.getQuiz());
	}
}
