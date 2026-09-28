package com.seoulchonnom.spec.file.entity.vo;

/**
 * 업로드 진행 상태. 보기용 이미지는 동기 업로드라 저장되는 순간 READY다.
 * PENDING은 브라우저가 저장소에 직접 올리는 RAW가 완료 검증을 기다리는 동안만 쓴다.
 */
public enum FileStatus {
	PENDING,
	READY
}
