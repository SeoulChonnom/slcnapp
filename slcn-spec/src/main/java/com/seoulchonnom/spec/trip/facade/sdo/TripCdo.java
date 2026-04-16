package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripCdo {
	@NotBlank(message = "나들이 일자는 필수값 입니다.")
	@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
	private String date;
	@NotBlank(message = "나들이 타입은 필수값 입니다.")
	@Pattern(regexp = "ryu|ayo")
	private String type;
	@NotBlank(message = "나들이 이름은 필수값 입니다.")
	private String name;
	@NotBlank(message = "나들이 로고 파일 경로는 필수값 입니다.")
	private String logo;

	@NotBlank(message = "나들이 지도 파일 경로는 필수값 입니다.")
	private String firstMap;
	private String secondMap;

	private String nextButtonText;
	private String previousButtonText;

	@NotBlank(message = "나들이 드라이브 URL은 필수값 입니다.")
	private String driveUrl;

	@NotBlank(message = "나들이 퀴즈 타이틀은 필수값 입니다.")
	private String quizTitle;
	@NotBlank(message = "나들이 퀴즈 정답은 필수값 입니다.")
	private String quizAnswer;
	@NotBlank(message = "나들이 퀴즈 정답 제목은 필수값 입니다.")
	private String quizAnswerTitle;
	@NotBlank(message = "나들이 퀴즈 정답 본문은 필수값 입니다.")
	private String quizAnswerText;
	@NotBlank(message = "나들이 퀴즈 오답 제목은 필수값 입니다.")
	private String quizErrorTitle;
	@NotBlank(message = "나들이 퀴즈 오답 타이틀은 필수값 입니다.")
	private String quizErrorText;

	@NotEmpty(message = "나들이 퀴즈 데이터은 필수값 입니다.")
	@Valid
	private List<QuizCdo> quizCdoList;
}
