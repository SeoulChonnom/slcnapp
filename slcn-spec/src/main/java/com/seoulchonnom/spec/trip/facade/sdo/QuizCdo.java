package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizCdo {
	@NotBlank(message = "나들이 퀴즈 타이틀은 필수값 입니다.")
	private String title;

	@NotBlank(message = "나들이 퀴즈 정답 제목은 필수값 입니다.")
	private String answerTitle;

	@NotBlank(message = "나들이 퀴즈 정답 본문은 필수값 입니다.")
	private String answerText;

	@NotBlank(message = "나들이 퀴즈 오답 제목은 필수값 입니다.")
	private String errorTitle;

	@NotBlank(message = "나들이 퀴즈 오답 본문은 필수값 입니다.")
	private String errorText;

	@NotEmpty(message = "나들이 퀴즈 보기 데이터는 필수값 입니다.")
	@Valid
	private List<OptionCdo> options;
}
