package com.seoulchonnom.aggregate.config;

import java.net.URI;
import java.nio.file.Paths;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.file.logic.FileAssetMigrationLogic;
import com.seoulchonnom.aggregate.file.storage.LocalFileObjectStorage;
import com.seoulchonnom.aggregate.file.storage.MultipartUploadStorage;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.storage.R2MultipartUploadStorage;
import com.seoulchonnom.aggregate.file.storage.R2ObjectStorage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 오브젝트 스토리지 어댑터 선택. 저장소 교체는 배포 환경 결정이므로 설정값 하나로 가른다.
 * r2가 아니면 로컬 디스크를 쓰므로 개발 환경과 테스트는 자격 증명 없이 그대로 돈다.
 * S3 클라이언트는 r2일 때만 빈으로 만들어 R2ObjectStorage와 R2MultipartUploadStorage가 함께 쓴다.
 */
@Slf4j
@Configuration
public class ObjectStorageConfiguration {
	private static final String PROVIDER_PROPERTY = "slcn.storage.provider";
	private static final String R2_PROVIDER = "r2";

	@Bean
	public ObjectStorage objectStorage(
		@Value("${slcn.storage.provider:local}") String provider,
		@Value("${slcn.upload.path}") String uploadPath,
		@Value("${slcn.storage.r2.bucket:}") String bucket,
		ObjectProvider<S3Client> s3Client,
		ObjectProvider<S3Presigner> s3Presigner) {
		if (!R2_PROVIDER.equalsIgnoreCase(provider)) {
			return new LocalFileObjectStorage(Paths.get(uploadPath).toAbsolutePath().normalize());
		}
		return new R2ObjectStorage(s3Client.getObject(), s3Presigner.getObject(), bucket);
	}

	/**
	 * 로컬 프로바이더에서는 만들지 않는다. 서명 URL 없이는 브라우저 직접 업로드가 불가능하므로,
	 * 사용하는 쪽은 Optional로 받아 비어 있으면 기능을 막는다.
	 */
	@Bean
	@ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = R2_PROVIDER)
	public MultipartUploadStorage multipartUploadStorage(S3Client s3Client, S3Presigner s3Presigner,
		@Value("${slcn.storage.r2.bucket:}") String bucket) {
		return new R2MultipartUploadStorage(s3Client, s3Presigner, bucket);
	}

	@Bean
	@ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = R2_PROVIDER)
	public S3Client s3Client(R2Endpoint r2Endpoint) {
		return S3Client.builder()
			.endpointOverride(r2Endpoint.uri())
			.region(r2Endpoint.region())
			.credentialsProvider(r2Endpoint.credentials())
			.serviceConfiguration(r2Endpoint.serviceConfiguration())
			.build();
	}

	@Bean
	@ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = R2_PROVIDER)
	public S3Presigner s3Presigner(R2Endpoint r2Endpoint) {
		return S3Presigner.builder()
			.endpointOverride(r2Endpoint.uri())
			.region(r2Endpoint.region())
			.credentialsProvider(r2Endpoint.credentials())
			.serviceConfiguration(r2Endpoint.serviceConfiguration())
			.build();
	}

	@Bean
	@ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = R2_PROVIDER)
	public R2Endpoint r2Endpoint(
		@Value("${slcn.storage.r2.endpoint:}") String endpoint,
		@Value("${slcn.storage.r2.region:auto}") String region,
		@Value("${slcn.storage.r2.access-key:}") String accessKey,
		@Value("${slcn.storage.r2.secret-key:}") String secretKey) {
		return new R2Endpoint(
			URI.create(endpoint),
			Region.of(region),
			StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
			// R2는 virtual-host 스타일 버킷 주소를 쓰지 않으므로 path-style을 강제한다.
			S3Configuration.builder().pathStyleAccessEnabled(true).build());
	}

	/**
	 * 기존 로컬 파일 백필. 반복 실행이 필요 없고 진행 상황을 배포 로그에서 바로 봐야 하므로 기동 시 1회로 둔다.
	 * 한 번 돌리고 나면 플래그를 내린다. 다시 켜도 이미 올라간 객체는 건너뛴다.
	 */
	@Bean
	@ConditionalOnProperty(name = "slcn.storage.migration.enabled", havingValue = "true")
	public ApplicationRunner objectStorageMigrationRunner(FileAssetMigrationLogic fileAssetMigrationLogic) {
		return args -> log.info("Object storage migration finished. {}",
			fileAssetMigrationLogic.migrateLegacyFiles());
	}

	/**
	 * S3Client와 S3Presigner가 같은 접속 정보를 쓰도록 한곳에서 만든다.
	 */
	public record R2Endpoint(URI uri, Region region, StaticCredentialsProvider credentials,
		S3Configuration serviceConfiguration) {
	}
}
