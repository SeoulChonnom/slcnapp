package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * 로컬 디스크 어댑터. 개발 환경과 R2 자격 증명이 없는 환경에서 쓴다.
 * 서명 URL을 만들 수 없으므로 presignedGetUrl은 항상 비어 있고, 호출자가 바이트를 직접 서빙한다.
 */
@RequiredArgsConstructor
public class LocalFileObjectStorage implements ObjectStorage {
	private final Path baseDirectory;

	@Override
	public void put(String key, Path source, String contentType) throws IOException {
		Path target = resolve(key);
		Files.createDirectories(target.getParent());
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public byte[] getBytes(String key) throws IOException {
		return Files.readAllBytes(resolve(key));
	}

	@Override
	public boolean exists(String key) {
		return Files.exists(resolve(key));
	}

	@Override
	public Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition) {
		return Optional.empty();
	}

	/**
	 * 키에 상위 경로가 섞여 들어와도 기준 디렉터리를 벗어나지 못하게 한다.
	 */
	private Path resolve(String key) {
		Path resolved = baseDirectory.resolve(key).normalize();
		if (!resolved.startsWith(baseDirectory)) {
			throw new IllegalArgumentException("Object key escapes base directory: " + key);
		}
		return resolved;
	}
}
