package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 주소 보정 요청. 보낸 필드만 보정으로 남고, 보내지 않은 필드는 보정 해제다(통째로 교체).
 * 본번·부번은 숫자 1~4자리이며 저장할 때 0 채움 4자리로 맞춘다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressOverrideUdo {
	private String roadAddress;
	private String bonbun;
	private String bubun;
}
