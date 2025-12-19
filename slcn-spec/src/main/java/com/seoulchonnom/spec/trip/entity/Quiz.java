package com.seoulchonnom.spec.trip.entity;

import org.springframework.beans.BeanUtils;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Quiz extends DomainEntity {
	private String quizIndex;
	private String answer;

	public Quiz(QuizCdo quizCdo) {
		Quiz quiz = new Quiz();
		BeanUtils.copyProperties(quizCdo, quiz);
	}

	public QuizRdo toRdo() {
		QuizRdo quizRdo = new QuizRdo();
		BeanUtils.copyProperties(this, quizRdo);
		return quizRdo;
	}
}
