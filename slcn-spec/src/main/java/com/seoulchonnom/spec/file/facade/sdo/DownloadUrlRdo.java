package com.seoulchonnom.spec.file.facade.sdo;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 원본 다운로드용 서명 URL. 브라우저가 location.href로 이동하면 저장소가 첨부 파일로 내려준다.
 * 토큰이 저장소로 가지 않고, 리다이렉트를 fetch로 따라가는 CORS 문제도 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlRdo {
	private String url;
	private String filename;
	private long size;
	private OffsetDateTime expiresAt;
}
