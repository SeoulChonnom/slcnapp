package com.seoulchonnom.spec.inspection.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
// JSON 컬럼에 실려 저장되므로 값 비교가 가능해야 한다. equals가 없으면 Hibernate의 더티 체크가
// 매 flush마다 이 컬렉션을 "변경됨"으로 보고, 내용이 그대로여도 UPDATE를 한 번 더 날린다.
// 그러면 @Version이 쓰기 한 번에 2씩 올라 조회 응답의 entityVersion이 곧바로 낡은 값이 된다.
@EqualsAndHashCode
public class QuestionChoice implements JsonSerializable {
	/**
	 * 답변이 참조하는 불변 식별자. label이 바뀌어도 과거 답변이 가리키는 대상은 유지된다.
	 */
	private String code;
	private String label;
	private int sortOrder;
}
