package com.seoulchonnom.aggregate.trip.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quiz", schema = "slcn")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuizJpo extends DomainEntityJpo {
	private String tripId;
	private String quizIndex;
	private String answer;
}
