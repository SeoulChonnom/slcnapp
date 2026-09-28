package com.seoulchonnom.aggregate.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.file.storage.LocalFileObjectStorage;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.storage.R2ObjectStorage;

import software.amazon.awssdk.services.s3.S3Client;

class ObjectStorageConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ObjectStorageConfiguration.class)
		.withPropertyValues("slcn.upload.path=build/tmp/object-storage-config-test");

	@Test
	void localProvider_shouldUseDiskAndCreateNoMultipartStorage() {
		runner.withPropertyValues("slcn.storage.provider=local").run(context -> {
			assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(LocalFileObjectStorage.class);
			assertThat(context).doesNotHaveBean(MultipartUploadStorage.class);
			assertThat(context).doesNotHaveBean(S3Client.class);
		});
	}

	@Test
	void r2Provider_shouldShareOneS3ClientBetweenBothAdapters() {
		runner.withPropertyValues(r2Properties("r2")).run(context -> {
			ObjectStorage objectStorage = context.getBean(ObjectStorage.class);
			MultipartUploadStorage multipartUploadStorage = context.getBean(MultipartUploadStorage.class);
			S3Client s3Client = context.getBean(S3Client.class);

			assertThat(objectStorage).isInstanceOf(R2ObjectStorage.class);
			assertThat(ReflectionTestUtils.getField(objectStorage, "s3Client")).isSameAs(s3Client);
			assertThat(ReflectionTestUtils.getField(multipartUploadStorage, "s3Client")).isSameAs(s3Client);
		});
	}

	@Test
	void r2Provider_shouldMatchCaseInsensitivelyLikeTheObjectStorageSwitch() {
		runner.withPropertyValues(r2Properties("R2")).run(context -> {
			assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(R2ObjectStorage.class);
			assertThat(context).hasSingleBean(MultipartUploadStorage.class);
		});
	}

	private static String[] r2Properties(String provider) {
		return new String[] {
			"slcn.storage.provider=" + provider,
			"slcn.storage.r2.endpoint=https://account.r2.cloudflarestorage.com",
			"slcn.storage.r2.bucket=slcn-media",
			"slcn.storage.r2.access-key=test-access",
			"slcn.storage.r2.secret-key=test-secret"
		};
	}
}
