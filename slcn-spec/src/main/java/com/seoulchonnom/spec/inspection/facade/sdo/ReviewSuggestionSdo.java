package com.seoulchonnom.spec.inspection.facade.sdo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 후기 제안 요청. 저장 전 폼에 입력 중인 값을 그대로 받는다 — 저장된 값을 읽지 않는다.
 * memo는 필수이고, pros는 사용자가 이미 쓴 장점을 AI에게 참고로 넘기는 선택 값이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSuggestionSdo {
	@NotBlank(message = "메모를 입력해야 제안할 수 있습니다.")
	@Size(max = 5000, message = "메모는 5000자 이하여야 합니다.")
	private String memo;
	@Size(max = 5000, message = "장점은 5000자 이하여야 합니다.")
	private String pros;
}
