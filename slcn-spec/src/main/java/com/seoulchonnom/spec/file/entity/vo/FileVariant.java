package com.seoulchonnom.spec.file.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 실제로 생성된 파생본 한 건. 포맷이 업로드 시점 환경에 따라 달라질 수 있으므로
 * 이름으로 재구성하지 않고 저장 파일명과 MIME 타입을 그대로 기록한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileVariant {
	private String variant;
	private String filename;
	private String mimeType;
}
