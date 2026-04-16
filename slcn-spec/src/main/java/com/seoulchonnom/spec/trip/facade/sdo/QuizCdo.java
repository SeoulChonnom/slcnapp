package com.seoulchonnom.spec.trip.facade.sdo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizCdo {
	@NotBlank(message = "나들이 퀴즈 ID는 필수값 입니다.")
	private String quizIndex;
	@NotBlank(message = "나들이 퀴즈 정답은 필수값 입니다.")
	private String answer;
}
