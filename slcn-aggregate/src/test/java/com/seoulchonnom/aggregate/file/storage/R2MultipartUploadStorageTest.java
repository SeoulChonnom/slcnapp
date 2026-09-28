package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

class R2MultipartUploadStorageTest {
	private static final String KEY = "originals/travel/72d768d4-2b05-48f9-bee8-fee3b52e909f.raf";

	private final S3Client s3Client = mock(S3Client.class);
	private final S3Presigner s3Presigner = mock(S3Presigner.class);
	private final R2MultipartUploadStorage storage = new R2MultipartUploadStorage(s3Client, s3Presigner, "slcn-media");

	@Test
	void create_shouldSendBucketKeyContentTypeAndReturnUploadId() throws Exception {
		when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
			.thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-1").build());

		String uploadId = storage.create(KEY, "image/x-fujifilm-raf");

		ArgumentCaptor<CreateMultipartUploadRequest> captor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
		verify(s3Client).createMultipartUpload(captor.capture());
		assertThat(uploadId).isEqualTo("upload-1");
		assertThat(captor.getValue().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().key()).isEqualTo(KEY);
		assertThat(captor.getValue().contentType()).isEqualTo("image/x-fujifilm-raf");
	}

	@Test
	void presignPart_shouldSignUploadIdPartNumberAndTtl() throws Exception {
		PresignedUploadPartRequest presigned = mock(PresignedUploadPartRequest.class);
		when(presigned.url()).thenReturn(new URL("https://r2.example/part?partNumber=3"));
		when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presigned);

		String url = storage.presignPart(KEY, "upload-1", 3, Duration.ofMinutes(30));

		ArgumentCaptor<UploadPartPresignRequest> captor = ArgumentCaptor.forClass(UploadPartPresignRequest.class);
		verify(s3Presigner).presignUploadPart(captor.capture());
		assertThat(url).isEqualTo("https://r2.example/part?partNumber=3");
		assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(30));
		assertThat(captor.getValue().uploadPartRequest().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().uploadPartRequest().key()).isEqualTo(KEY);
		assertThat(captor.getValue().uploadPartRequest().uploadId()).isEqualTo("upload-1");
		assertThat(captor.getValue().uploadPartRequest().partNumber()).isEqualTo(3);
	}

	@Test
	void complete_shouldSendPartsSortedByPartNumber() throws Exception {
		storage.complete(KEY, "upload-1", List.of(
			new MultipartUploadStorage.UploadedPart(2, "\"etag-2\""),
			new MultipartUploadStorage.UploadedPart(1, "\"etag-1\"")));

		ArgumentCaptor<CompleteMultipartUploadRequest> captor =
			ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
		verify(s3Client).completeMultipartUpload(captor.capture());
		assertThat(captor.getValue().uploadId()).isEqualTo("upload-1");
		assertThat(captor.getValue().multipartUpload().parts())
			.extracting(CompletedPart::partNumber, CompletedPart::eTag)
			.containsExactly(tuple(1, "\"etag-1\""), tuple(2, "\"etag-2\""));
	}

	@Test
	void abort_shouldSendUploadId() throws Exception {
		storage.abort(KEY, "upload-1");

		ArgumentCaptor<AbortMultipartUploadRequest> captor = ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
		verify(s3Client).abortMultipartUpload(captor.capture());
		assertThat(captor.getValue().key()).isEqualTo(KEY);
		assertThat(captor.getValue().uploadId()).isEqualTo("upload-1");
	}

	@Test
	void abort_shouldTreatMissingUploadAsAlreadyDone() {
		when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
			.thenThrow(NoSuchUploadException.builder().message("gone").build());

		assertThatCode(() -> storage.abort(KEY, "upload-1")).doesNotThrowAnyException();
	}

	@Test
	void headSize_shouldReturnContentLength() throws Exception {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenReturn(HeadObjectResponse.builder().contentLength(83886080L).build());

		assertThat(storage.headSize(KEY)).isEqualTo(83886080L);
	}

	@Test
	void readRange_shouldRequestInclusiveByteRange() throws Exception {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[16]));

		byte[] bytes = storage.readRange(KEY, 0, 15);

		ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
		verify(s3Client).getObjectAsBytes(captor.capture());
		assertThat(bytes).hasSize(16);
		assertThat(captor.getValue().range()).isEqualTo("bytes=0-15");
	}

	@Test
	void delete_shouldSendBucketAndKey() throws Exception {
		storage.delete(KEY);

		ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client).deleteObject(captor.capture());
		assertThat(captor.getValue().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().key()).isEqualTo(KEY);
	}

	@Test
	void sdkFailures_shouldSurfaceAsIoException() {
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(SdkClientException.create("boom"));

		assertThatThrownBy(() -> storage.headSize(KEY)).isInstanceOf(IOException.class);
	}
}
