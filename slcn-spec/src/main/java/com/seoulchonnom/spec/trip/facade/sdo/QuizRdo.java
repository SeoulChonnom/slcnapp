package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuizRdo {
	private String title;
	private List<OptionRdo> options;
}
