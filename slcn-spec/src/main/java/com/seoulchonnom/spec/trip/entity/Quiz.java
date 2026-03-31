package com.seoulchonnom.spec.trip.entity;

import org.springframework.beans.BeanUtils;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;

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
public class Quiz extends DomainEntity {
	private String tripId;
	private String quizIndex;
	private String answer;

	public Quiz(QuizCdo quizCdo) {
		Quiz quiz = new Quiz();
		BeanUtils.copyProperties(quizCdo, quiz);
	}
}
