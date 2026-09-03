package com.seoulchonnom.aggregate.config;

import java.net.URI;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.file.storage.LocalFileObjectStorage;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.storage.R2ObjectStorage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 오브젝트 스토리지 어댑터 선택. 저장소 교체는 배포 환경 결정이므로 설정값 하나로 가른다.
 * r2가 아니면 로컬 디스크를 쓰므로 개발 환경과 테스트는 자격 증명 없이 그대로 돈다.
 */
@Configuration
public class ObjectStorageConfiguration {
	@Bean
	public ObjectStorage objectStorage(
		@Value("${slcn.storage.provider:local}") String provider,
		@Value("${slcn.upload.path}") String uploadPath,
		@Value("${slcn.storage.r2.endpoint:}") String endpoint,
		@Value("${slcn.storage.r2.region:auto}") String region,
		@Value("${slcn.storage.r2.bucket:}") String bucket,
		@Value("${slcn.storage.r2.access-key:}") String accessKey,
		@Value("${slcn.storage.r2.secret-key:}") String secretKey) {
		if (!"r2".equalsIgnoreCase(provider)) {
			return new LocalFileObjectStorage(Paths.get(uploadPath).toAbsolutePath().normalize());
		}

		StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
			AwsBasicCredentials.create(accessKey, secretKey));
		URI endpointUri = URI.create(endpoint);
		// R2는 virtual-host 스타일 버킷 주소를 쓰지 않으므로 path-style을 강제한다.
		S3Configuration serviceConfiguration = S3Configuration.builder()
			.pathStyleAccessEnabled(true)
			.build();

		S3Client s3Client = S3Client.builder()
			.endpointOverride(endpointUri)
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.serviceConfiguration(serviceConfiguration)
			.build();
		S3Presigner s3Presigner = S3Presigner.builder()
			.endpointOverride(endpointUri)
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.serviceConfiguration(serviceConfiguration)
			.build();

		return new R2ObjectStorage(s3Client, s3Presigner, bucket);
	}
}
