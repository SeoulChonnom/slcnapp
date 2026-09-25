package com.seoulchonnom.spec.file.facade.sdo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawUploadSessionRdo {
	private String fileId;
	private String uploadId;
	/**
	 * 마지막 파트를 뺀 모든 파트의 크기. 브라우저는 이 크기로 파일을 잘라 올린다.
	 */
	private long partSize;
	/**
	 * 파트 URL 만료 시각. 넘기면 세션을 취소하고 처음부터 다시 올린다.
	 */
	private OffsetDateTime expiresAt;
	private List<RawUploadPartUrlRdo> parts = new ArrayList<>();
}
