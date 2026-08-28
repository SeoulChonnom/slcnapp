package com.seoulchonnom.spec.file.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFileRdo {
	private byte[] image;
	private String mimeType;
	/**
	 * 실제로 응답한 파생본 이름. 원본을 응답했다면 "original"이다. ETag를 만들 때 원본과 파생본을 구분하는 데 쓴다.
	 */
	private String variant;
}
