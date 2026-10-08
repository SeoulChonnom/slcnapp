package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 후기 제안 요청. 저장 전 폼에 입력 중인 값을 그대로 받는다 — 저장된 값을 읽지 않는다.
 * memo와 pros 중 하나는 비어 있지 않아야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSuggestionSdo {
	private String memo;
	private String pros;
}
