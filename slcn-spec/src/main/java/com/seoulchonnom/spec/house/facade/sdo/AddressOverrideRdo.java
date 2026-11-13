package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressOverrideRdo {
	private String roadAddress;
	/** 앞 0을 없앤 본번 */
	private String bonbun;
	private String bubun;
}
