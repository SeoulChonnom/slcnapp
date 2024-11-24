package com.seoulchonnom.slcnapp.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Trip {
	@Id
	@Column(length = 8)
	private String date;

	@Id
	@Column(length=1)
	private String type;

	@Column(length=10)
	private String info1;

	@Column(length=30)
	private String info2;

	private String logo;

	private String map1;

	private String map2;

	@Column(length=30)
	private String button1;

	@Column(length=30)
	private String button2;

	private String drive;

	@Column(length=50)
	private String quizTitle;

	@Column(length=2)
	private String quizAnswer;

	@Column(length=50)
	private String quizAnswerTitle;

	@Column(length=50)
	private String quizAnswerText;

	@Column(length=50)
	private String quizErrorTitle;

	@Column(length=50)
	private String quizErrorText;
}
