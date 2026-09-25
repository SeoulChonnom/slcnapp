package com.seoulchonnom.spec.file.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawUploadPartCdo {
	private Integer partNumber;
	/**
	 * 파트 PUT 응답의 ETag 헤더 값. 따옴표를 포함한 그대로 보낸다.
	 */
	private String etag;
}
