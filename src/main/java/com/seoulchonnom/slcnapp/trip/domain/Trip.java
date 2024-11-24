package com.seoulchonnom.slcnapp.trip.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(length = 8, nullable = false)
	private String date;

	@Column(length = 1, nullable = false)
	private String type;

	@Column(length = 10, nullable = false)
	private String info1;

	@Column(length = 30, nullable = false)
	private String info2;

	@Column(nullable = false)
	private String logo;

	@Column(nullable = false)
	private String map1;

	private String map2;

	@Column(length=30)
	private String button1;

	@Column(length=30)
	private String button2;

	@Column(nullable = false)
	private String drive;

	@Column(length = 50, nullable = false)
	private String quizTitle;

	@Column(length = 2, nullable = false)
	private String quizAnswer;

	@Column(length = 50, nullable = false)
	private String quizAnswerTitle;

	@Column(length = 50, nullable = false)
	private String quizAnswerText;

	@Column(length = 50, nullable = false)
	private String quizErrorTitle;

	@Column(length = 50, nullable = false)
	private String quizErrorText;

	@Builder.Default
	@OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
	private List<Quiz> quizList = new ArrayList<>();
}
