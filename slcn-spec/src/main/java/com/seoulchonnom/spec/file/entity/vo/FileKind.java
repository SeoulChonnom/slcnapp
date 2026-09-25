package com.seoulchonnom.spec.file.entity.vo;

/**
 * 자산의 쓰임. IMAGE는 화면에 보이는 사진이고, RAW는 다운로드 전용 첨부다.
 * RAW는 서버가 디코딩하지 않으므로 파생본도 인라인 조회도 없다.
 */
public enum FileKind {
	IMAGE,
	RAW
}
