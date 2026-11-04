package com.seoulchonnom.spec.house.entity.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자가 보정한 주소. 보낸 필드만 값이 있고 나머지는 null(보정 없음)이다.
 * JSON 컬럼 값이라 equals가 없으면 더티 체크가 매번 변경으로 판정한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AddressOverride {
	private String roadAddress;
	private String bonbun;
	private String bubun;
}
