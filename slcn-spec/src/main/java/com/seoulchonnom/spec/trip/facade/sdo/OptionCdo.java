package com.seoulchonnom.spec.trip.facade.sdo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OptionCdo {
	@NotBlank(message = "나들이 퀴즈 보기 내용은 필수값 입니다.")
	private String text;

	@JsonProperty("isCorrect")
	private boolean correct;
}
