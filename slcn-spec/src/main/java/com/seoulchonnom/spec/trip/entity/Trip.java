package com.seoulchonnom.spec.trip.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
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
	private String logo;
	private String firstMap;
	private String secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private TripQuiz quiz;

	public Trip(TripCdo tripCdo, String id) {
		super(id);
		this.date = tripCdo.getDate();
		this.type = tripCdo.getType();
		this.name = tripCdo.getName();
		this.logo = tripCdo.getLogo();
		this.firstMap = tripCdo.getFirstMap();
		this.secondMap = tripCdo.getSecondMap();
		this.nextButtonText = tripCdo.getNextButtonText();
		this.previousButtonText = tripCdo.getPreviousButtonText();
		this.driveUrl = tripCdo.getDriveUrl();
		this.quiz = new TripQuiz(tripCdo.getQuiz(), id);
	}
}
