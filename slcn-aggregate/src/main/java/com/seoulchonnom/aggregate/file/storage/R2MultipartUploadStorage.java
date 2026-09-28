package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * S3 호환 multipart 업로드 어댑터. R2ObjectStorage와 같은 클라이언트를 쓰고, SDK 예외는 IOException으로 감싼다.
 */
@Slf4j
@RequiredArgsConstructor
public class R2MultipartUploadStorage implements MultipartUploadStorage {
	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	@Override
	public String create(String key, String contentType) throws IOException {
		try {
			return s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build()).uploadId();
		} catch (SdkException e) {
			throw new IOException("Failed to create multipart upload: " + key, e);
		}
	}

	@Override
	public String presignPart(String key, String uploadId, int partNumber, Duration ttl) throws IOException {
		try {
			UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
				.bucket(bucket)
				.key(key)
				.uploadId(uploadId)
				.partNumber(partNumber)
				.build();
			return s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
				.signatureDuration(ttl)
				.uploadPartRequest(uploadPartRequest)
				.build()).url().toString();
		} catch (SdkException e) {
			throw new IOException("Failed to presign upload part: " + key + "#" + partNumber, e);
		}
	}

	/**
	 * S3는 파트가 번호 오름차순이어야 완료를 받아 준다. 브라우저가 병렬로 올려 순서가 섞여 와도 되게 정렬한다.
	 */
	@Override
	public void complete(String key, String uploadId, List<UploadedPart> parts) throws IOException {
		List<CompletedPart> completedParts = parts.stream()
			.sorted(Comparator.comparingInt(UploadedPart::partNumber))
			.map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.etag()).build())
			.toList();
		try {
			s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
				.bucket(bucket)
				.key(key)
				.uploadId(uploadId)
				.multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
				.build());
		} catch (SdkException e) {
			throw new IOException("Failed to complete multipart upload: " + key, e);
		}
	}

	@Override
	public void abort(String key, String uploadId) throws IOException {
		try {
			s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
				.bucket(bucket)
				.key(key)
				.uploadId(uploadId)
				.build());
		} catch (NoSuchUploadException e) {
			log.debug("Multipart upload already gone. key={}, uploadId={}", key, uploadId);
		} catch (SdkException e) {
			throw new IOException("Failed to abort multipart upload: " + key, e);
		}
	}

	@Override
	public long headSize(String key) throws IOException {
		try {
			return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
		} catch (SdkException e) {
			throw new IOException("Failed to head object: " + key, e);
		}
	}

	@Override
	public byte[] readRange(String key, long start, long endInclusive) throws IOException {
		try {
			return s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.range("bytes=" + start + "-" + endInclusive)
				.build()).asByteArray();
		} catch (SdkException e) {
			throw new IOException("Failed to read object range: " + key, e);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			throw new IOException("Failed to delete object: " + key, e);
		}
	}
}
