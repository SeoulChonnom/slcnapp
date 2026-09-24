package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class R2ObjectStorageTest {
	private final S3Client s3Client = mock(S3Client.class);
	private final S3Presigner s3Presigner = mock(S3Presigner.class);
	private final R2ObjectStorage storage = new R2ObjectStorage(s3Client, s3Presigner, "slcn-media");

	@TempDir
	Path tempDir;

	@Test
	void put_shouldSendBucketKeyAndContentType() throws Exception {
		Path source = Files.write(tempDir.resolve("a.png"), new byte[] {1, 2, 3});
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		storage.put("originals/travel/a.png", source, "image/png");

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().key()).isEqualTo("originals/travel/a.png");
		assertThat(captor.getValue().contentType()).isEqualTo("image/png");
	}

	@Test
	void put_shouldWrapSdkFailuresAsIoExceptionSoCallersHandleOneType() throws Exception {
		Path source = Files.write(tempDir.resolve("a.png"), new byte[] {1});
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.create("boom"));

		assertThatThrownBy(() -> storage.put("originals/travel/a.png", source, "image/png"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void getBytes_shouldReturnObjectBytes() throws Exception {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {7, 7}));

		assertThat(storage.getBytes("derived/travel/a_home-thumb.webp")).containsExactly(7, 7);
	}

	@Test
	void getBytes_shouldWrapMissingObjectAsIoException() {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertThatThrownBy(() -> storage.getBytes("derived/travel/absent.webp"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void exists_shouldBeFalseWhenHeadObjectReportsNoSuchKey() {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertThat(storage.exists("originals/travel/absent.png")).isFalse();
	}

	@Test
	void exists_shouldBeTrueWhenHeadObjectSucceeds() {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenReturn(HeadObjectResponse.builder().build());

		assertThat(storage.exists("originals/travel/a.png")).isTrue();
	}

	@Test
	void presignedGetUrl_shouldSignBucketKeyAndTtl() throws Exception {
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://r2.example.com/a.png?X-Amz-Signature=sig"));
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		var url = storage.presignedGetUrl("originals/travel/a.png", Duration.ofSeconds(300), null);

		assertThat(url).contains("https://r2.example.com/a.png?X-Amz-Signature=sig");
		ArgumentCaptor<GetObjectPresignRequest> captor =
			ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(s3Presigner).presignGetObject(captor.capture());
		assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
		assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("originals/travel/a.png");
		assertThat(captor.getValue().getObjectRequest().responseContentDisposition()).isNull();
	}

	@Test
	void presignedGetUrl_shouldCarryContentDispositionSoDownloadsKeepTheirFilename() throws Exception {
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://r2.example.com/a.png?X-Amz-Signature=sig"));
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		storage.presignedGetUrl("originals/travel/a.png", Duration.ofSeconds(300),
			"attachment; filename*=UTF-8''%EC%82%AC%EC%A7%84.png");

		ArgumentCaptor<GetObjectPresignRequest> captor =
			ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(s3Presigner).presignGetObject(captor.capture());
		assertThat(captor.getValue().getObjectRequest().responseContentDisposition())
			.isEqualTo("attachment; filename*=UTF-8''%EC%82%AC%EC%A7%84.png");
	}
}
