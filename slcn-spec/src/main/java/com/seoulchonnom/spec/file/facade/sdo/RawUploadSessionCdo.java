package com.seoulchonnom.spec.file.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawUploadSessionCdo {
	/**
	 * 자산 타입. RAW는 여행 앨범에만 붙으므로 travel만 받는다.
	 */
	private String type;
	private String filename;
	/**
	 * 브라우저가 선언한 바이트 크기. 파트 수를 정하고, 완료 시 저장소의 실제 크기와 비교한다.
	 */
	private Long size;
}
