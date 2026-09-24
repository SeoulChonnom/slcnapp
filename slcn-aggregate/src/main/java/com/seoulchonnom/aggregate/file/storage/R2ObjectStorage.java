package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 호환 오브젝트 스토리지 어댑터. Cloudflare R2를 대상으로 하지만 endpoint만 바꾸면 S3에서도 동작한다.
 * SDK 예외는 전부 IOException으로 감싸 호출자가 저장소 종류를 몰라도 되게 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class R2ObjectStorage implements ObjectStorage {
	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	@Override
	public void put(String key, Path source, String contentType) throws IOException {
		try {
			s3Client.putObject(PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build(), RequestBody.fromFile(source));
		} catch (SdkException e) {
			throw new IOException("Failed to put object: " + key, e);
		}
	}

	@Override
	public byte[] getBytes(String key) throws IOException {
		try {
			return s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build()).asByteArray();
		} catch (SdkException e) {
			throw new IOException("Failed to get object: " + key, e);
		}
	}

	/**
	 * HeadObject 실패는 백필의 중복 업로드로만 이어지고 업로드 자체는 멱등하므로, 없는 것으로 취급한다.
	 */
	@Override
	public boolean exists(String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (SdkException e) {
			log.debug("HeadObject reported the object as unavailable. key={}", key, e);
			return false;
		}
	}

	@Override
	public Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition) {
		GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
			.bucket(bucket)
			.key(key);
		if (contentDisposition != null) {
			// 파일명과 attachment 여부를 서명된 쿼리 파라미터로 넘긴다. 바이트가 서버를 통과하지 않아도 저장 이름이 유지된다.
			getObjectRequest.responseContentDisposition(contentDisposition);
		}

		return Optional.of(s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
			.signatureDuration(ttl)
			.getObjectRequest(getObjectRequest.build())
			.build()).url().toString());
	}
}
