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
	/**
	 * 다운로드 응답의 Content-Disposition에 쓰는 파일명.
	 */
	private String downloadFilename;
	/**
	 * 원본처럼 서버가 바이트를 통과시키지 않는 자산의 서명된 조회 URL.
	 * 값이 있으면 image는 비어 있고, 호출자는 302로 이 URL을 넘겨야 한다.
	 */
	private String redirectUrl;
}
