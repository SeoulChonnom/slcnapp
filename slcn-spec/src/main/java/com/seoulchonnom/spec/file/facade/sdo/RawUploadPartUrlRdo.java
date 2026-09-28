package com.seoulchonnom.spec.file.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawUploadPartUrlRdo {
	private int partNumber;
	/**
	 * 이 파트를 PUT할 서명 URL. 요청 헤더에 인증 토큰을 싣지 않는다.
	 */
	private String url;
}
