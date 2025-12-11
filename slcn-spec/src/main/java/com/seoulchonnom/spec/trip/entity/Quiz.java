package com.seoulchonnom.spec.trip.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

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
}
