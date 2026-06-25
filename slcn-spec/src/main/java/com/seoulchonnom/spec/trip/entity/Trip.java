package com.seoulchonnom.spec.trip.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

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
	private String logoFileId;
	private String firstMapFileId;
	private String secondMapFileId;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private Quiz quiz;

	public Trip(String id, String date, String type, String name, String logoFileId, String firstMapFileId,
		String secondMapFileId, String nextButtonText, String previousButtonText, String driveUrl, Quiz quiz) {
		super(id);
		this.date = date;
		this.type = type;
		this.name = name;
		this.logoFileId = logoFileId;
		this.firstMapFileId = firstMapFileId;
		this.secondMapFileId = secondMapFileId;
		this.nextButtonText = nextButtonText;
		this.previousButtonText = previousButtonText;
		this.driveUrl = driveUrl;
		this.quiz = quiz;
	}
}
