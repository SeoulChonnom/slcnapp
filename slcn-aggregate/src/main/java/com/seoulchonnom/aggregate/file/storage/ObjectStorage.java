package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 이미지 바이트의 저장소. 구현은 로컬 디스크와 S3 호환 오브젝트 스토리지 두 가지다.
 * 어느 쪽을 쓸지는 인프라 결정이므로 호출자는 이 포트만 안다.
 */
public interface ObjectStorage {
	/**
	 * 업로드는 항상 임시 파일에서 올린다. 서버 힙에 바이트를 올리지 않기 위함이다.
	 */
	void put(String key, Path source, String contentType) throws IOException;

	/**
	 * 파생본처럼 작은 객체만 읽는다. 원본은 presignedGetUrl로 넘긴다.
	 */
	byte[] getBytes(String key) throws IOException;

	boolean exists(String key);

	/**
	 * 서명된 조회 URL. 서명을 지원하지 않는 구현은 비어 있는 값을 돌려주고,
	 * 호출자는 지금까지처럼 바이트를 직접 읽어 응답한다.
	 *
	 * @param contentDisposition 첨부 다운로드로 내려줄 때의 Content-Disposition 값. 인라인 조회면 null이다.
	 */
	Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition);
}
