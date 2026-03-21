package com.seoulchonnom.aggregate.trip.store.jpo;

import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trip", schema = "slcn")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripJpo extends DomainEntityJpo {
	private String date;
	private String type;
	private String name;
	private String logo;

	private String firstMap;
	private String secondMap;

	private String nextButtonText;
	private String previousButtonText;

	private String driveUrl;

	private String quizTitle;
	private String quizAnswer;
	private String quizAnswerTitle;
	private String quizAnswerText;
	private String quizErrorTitle;
	private String quizErrorText;

	@OneToMany(mappedBy = "tripId", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private List<QuizJpo> quizList;
}
