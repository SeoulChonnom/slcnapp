package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 브라우저가 저장소에 직접 올리는 대용량 파일(RAW)을 위한 포트.
 * 바이트는 서버를 거치지 않고, 서버는 세션 발급과 완료 검증만 한다.
 * 서명 URL을 만들 수 없는 로컬 디스크는 이 포트를 구현하지 않는다. 그래서 ObjectStorage와 분리한다.
 */
public interface MultipartUploadStorage {
	/**
	 * @return 저장소가 발급한 multipart upload id
	 */
	String create(String key, String contentType) throws IOException;

	/**
	 * 파트 하나를 PUT할 수 있는 서명 URL. 서명은 로컬 계산이라 네트워크 호출이 없다.
	 */
	String presignPart(String key, String uploadId, int partNumber, Duration ttl) throws IOException;

	void complete(String key, String uploadId, List<UploadedPart> parts) throws IOException;

	/**
	 * 이미 중단됐거나 완료되어 없는 업로드도 성공으로 본다. 취소와 정리 스케줄러가 재시도해도 안전해야 한다.
	 */
	void abort(String key, String uploadId) throws IOException;

	long headSize(String key) throws IOException;

	/**
	 * 양 끝을 포함하는 바이트 범위를 읽는다. 매직 바이트 확인처럼 앞부분 몇 바이트만 볼 때 쓴다.
	 */
	byte[] readRange(String key, long start, long endInclusive) throws IOException;

	/**
	 * 없는 키를 지워도 성공이다.
	 */
	void delete(String key) throws IOException;

	/**
	 * 브라우저가 각 파트 PUT 응답에서 받은 ETag.
	 */
	record UploadedPart(int partNumber, String etag) {
	}
}
