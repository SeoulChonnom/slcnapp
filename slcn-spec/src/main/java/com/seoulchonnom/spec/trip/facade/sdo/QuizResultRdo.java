package com.seoulchonnom.spec.trip.facade.sdo;

import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
public class QuizResultRdo {
	private boolean correct;
	private String title;
	private String text;
}
