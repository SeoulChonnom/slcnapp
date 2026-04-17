package com.seoulchonnom.spec.trip.facade.sdo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
	@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "나들이 일자는 yyyy-MM-dd 형식이어야 합니다.")
	private String date;
	@NotBlank(message = "나들이 타입은 필수값 입니다.")
	@Pattern(regexp = "^(ryu|ayo)$", message = "나들이 타입은 ryu 또는 ayo 여야 합니다.")
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

	@NotNull(message = "나들이 퀴즈 데이터는 필수값 입니다.")
	@Valid
	private TripQuizCdo quiz;
}
