package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.exception.PresignedUrlNotSupportedException;
import com.seoulchonnom.aggregate.file.exception.RawFileNotViewableException;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;
import com.seoulchonnom.spec.file.facade.sdo.DownloadUrlRdo;

class FileLogicTest {
	private static final String UUID_NAME = "72d768d4-2b05-48f9-bee8-fee3b52e909f";

	private final FileUtils fileUtils = mock(FileUtils.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final ObjectStorage objectStorage = mock(ObjectStorage.class);
	private final FileLogic fileLogic = new FileLogic(fileUtils, fileAssetStore, objectStorage);

	@TempDir
	Path tempDir;

	@Test
	void uploadFiles_shouldSaveAssetsAndReturnStoredMetadata() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original)).thenReturn(new FileUtils.ImageProfile(1600, 900,
			List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp"))));
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		List<FileAsset> result = fileLogic.uploadFiles(List.of(file), "travel");

		assertThat(result).containsExactly(fileAsset);
		assertThat(fileAsset.getWidth()).isEqualTo(1600);
		assertThat(fileAsset.getHeight()).isEqualTo(900);
		assertThat(fileAsset.variantNames()).containsExactly("home-thumb");
		verify(fileAssetStore).save(fileAsset);
	}

	@Test
	void uploadFiles_shouldPutOriginalAndVariantsUnderTheirOwnPrefixes() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		Path variant = Files.write(staging.resolve("stored_home-thumb.webp"), new byte[] {2});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original)).thenReturn(new FileUtils.ImageProfile(1600, 900,
			List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp"))));
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		fileLogic.uploadFiles(List.of(file), "travel");

		verify(objectStorage).put("originals/travel/stored.png", original, "image/png");
		verify(objectStorage).put("derived/travel/stored_home-thumb.webp", variant, "image/webp");
	}

	@Test
	void uploadFiles_shouldNotSaveMetadataWhenTheObjectUploadFails() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original))
			.thenReturn(new FileUtils.ImageProfile(1600, 900, List.of()));
		doThrow(new IOException("boom")).when(objectStorage)
			.put("originals/travel/stored.png", original, "image/png");

		assertThatThrownBy(() -> fileLogic.uploadFiles(List.of(file), "travel"))
			.isInstanceOf(FileUploadException.class);
		verify(fileAssetStore, never()).save(any());
	}

	@Test
	void uploadFiles_shouldAlwaysCleanTheStagingDirectory() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original))
			.thenReturn(new FileUtils.ImageProfile(1600, 900, List.of()));
		doThrow(new IOException("boom")).when(objectStorage)
			.put("originals/travel/stored.png", original, "image/png");

		assertThatThrownBy(() -> fileLogic.uploadFiles(List.of(file), "travel"))
			.isInstanceOf(FileUploadException.class);
		verify(fileUtils).deleteStaging(staging);
	}

	@Test
	void uploadFiles_shouldRejectEmptyFiles() {
		assertThatThrownBy(() -> fileLogic.uploadFiles(List.of(), "travel"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("files는 필수입니다.");
	}

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(fileLogic, "presignedTtlSeconds", 300L);
		// 기본값은 "서명할 수 없는 저장소"다. 리다이렉트를 검증하는 테스트만 개별로 덮어쓴다.
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), any()))
			.thenReturn(Optional.empty());
	}

	@Test
	void getImageFileById_shouldRedirectToAPresignedOriginalUrl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(eq("originals/travel/stored.png"), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		assertThat(result.getImage()).isNull();
		assertThat(result.getVariant()).isEqualTo("original");
		verify(fileUtils).isValidFileRef("travel", "stored.png");
	}

	@Test
	void getImageFileById_shouldSignForTheConfiguredTtl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		fileLogic.getImageFileById("file-1");

		verify(objectStorage).presignedGetUrl("originals/travel/stored.png", Duration.ofSeconds(300), null);
	}

	@Test
	void getImageFileById_shouldServeOriginalBytesWhenStorageCannotSign() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getRedirectUrl()).isNull();
		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getMimeType()).isEqualTo("image/png");
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldRaiseFilePathInvalidWhenTheOriginalIsUnreadable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenThrow(new IOException("missing"));

		assertThatThrownBy(() -> fileLogic.getImageFileById("file-1"))
			.isInstanceOf(FilePathInvalidException.class);
	}

	@Test
	void getImageFileById_shouldServeVariantBytesFromTheDerivedPrefix() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9, 9});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(9, 9);
		assertThat(result.getMimeType()).isEqualTo("image/webp");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
		assertThat(result.getRedirectUrl()).isNull();
	}

	@Test
	void getImageFileById_shouldNeverRedirectForVariantsSoTheirCacheKeysStayStable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9});
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), any()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getRedirectUrl()).isNull();
		assertThat(result.getImage()).containsExactly(9);
	}

	@Test
	void getImageFileById_shouldServeJpegVariantWhenThatIsWhatWasRecorded() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.jpg", "image/jpeg")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.jpg")).thenReturn(new byte[] {8});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getMimeType()).isEqualTo("image/jpeg");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantWasNeverGenerated() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantIsRecordedButUnreadable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp"))
			.thenThrow(new IOException("missing"));
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldCarryTheUploadedFilenameForDownloads() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "여행 사진.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("여행 사진.png");
	}

	@Test
	void getImageFileById_shouldStripPathAndControlCharactersFromTheUploadedFilename() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "../etc/pass\"wd.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("passwd.png");
	}

	@Test
	void getImageFileById_shouldNameVariantDownloadsAfterTheOriginalFile() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getDownloadFilename()).isEqualTo("cover_home-thumb.webp");
	}

	@Test
	void downloadImageFileById_shouldPutAttachmentDispositionIntoTheSignedUrl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "여행 사진.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), anyString()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.downloadImageFileById("file-1", null);

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
		verify(objectStorage).presignedGetUrl(eq("originals/travel/stored.png"), any(Duration.class),
			disposition.capture());
		assertThat(disposition.getValue()).startsWith("attachment;");
		// 한글 파일명은 RFC 5987로 인코딩되어야 한다.
		assertThat(disposition.getValue()).contains("filename*=UTF-8''");
	}

	@Test
	void downloadImageFileById_shouldFallBackToAGenericNameWhenTheAssetHasNoUsableFilename() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "", "", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), anyString()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		fileLogic.downloadImageFileById("file-1", null);

		ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
		verify(objectStorage).presignedGetUrl(anyString(), any(Duration.class), disposition.capture());
		assertThat(disposition.getValue()).contains("download");
	}

	@Test
	void getImageFile_shouldRedirectForOriginalFilenames() {
		when(objectStorage.presignedGetUrl(eq("originals/logo/" + UUID_NAME + ".png"), any(Duration.class),
			isNull())).thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFile("logo", UUID_NAME + ".png");

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		verify(fileUtils).isValidFileRef("logo", UUID_NAME + ".png");
	}

	@Test
	void getImageFile_shouldServeVariantFilenamesAsBytesFromTheDerivedPrefix() throws Exception {
		when(objectStorage.getBytes("derived/logo/" + UUID_NAME + "_home-thumb.webp"))
			.thenReturn(new byte[] {5});

		var result = fileLogic.getImageFile("logo", UUID_NAME + "_home-thumb.webp");

		assertThat(result.getImage()).containsExactly(5);
		assertThat(result.getMimeType()).isEqualTo("image/webp");
		assertThat(result.getRedirectUrl()).isNull();
	}

	@Test
	void uploadFile_shouldSaveWithoutVariantsWhenNoVariantPermitIsFree() throws Exception {
		Semaphore permits = (Semaphore)ReflectionTestUtils.getField(fileLogic, "variantPermits");
		permits.acquire(2);
		MockMultipartFile file = new MockMultipartFile("file", "travel.jpg", "image/jpeg", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.jpg", UUID_NAME + ".jpg", "image/jpeg", 1L);
		Path staging = Files.createDirectories(tempDir.resolve("busy"));
		Path original = Files.write(staging.resolve(UUID_NAME + ".jpg"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel")).thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.readProfile(original)).thenReturn(new FileUtils.ImageProfile(5152, 7728, List.of()));
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		FileAsset saved = fileLogic.uploadFile(file, "travel");

		assertThat(saved.getWidth()).isEqualTo(5152);
		assertThat(saved.getHeight()).isEqualTo(7728);
		assertThat(saved.getVariants()).isEmpty();
		verify(fileUtils, never()).writeVariants(any(), any());
		verify(objectStorage, times(1)).put(anyString(), any(Path.class), anyString());
	}

	@Test
	void uploadFile_shouldReleaseVariantPermitEvenWhenGenerationThrows() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "travel.jpg", "image/jpeg", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.jpg", UUID_NAME + ".jpg", "image/jpeg", 1L);
		Path staging = Files.createDirectories(tempDir.resolve("failing"));
		Path original = Files.write(staging.resolve(UUID_NAME + ".jpg"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel")).thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original)).thenThrow(new IllegalStateException("decoder crashed"));

		assertThatThrownBy(() -> fileLogic.uploadFile(file, "travel")).isInstanceOf(IllegalStateException.class);

		Semaphore permits = (Semaphore)ReflectionTestUtils.getField(fileLogic, "variantPermits");
		assertThat(permits.availablePermits()).isEqualTo(2);
	}

	@Test
	void getImageFile_shouldRefuseRawAttachmentOnPathBasedLookup() {
		assertThatThrownBy(() -> fileLogic.getImageFile("travel", UUID_NAME + ".raf"))
			.isInstanceOf(FilePathInvalidException.class);
		assertThatThrownBy(() -> fileLogic.getImageFile("travel", UUID_NAME + ".RAF"))
			.isInstanceOf(FilePathInvalidException.class);
		verifyNoInteractions(objectStorage);
	}

	@Test
	void getDownloadUrl_shouldPresignOriginalAsUtf8Attachment() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "제주 여행.jpg", UUID_NAME + ".jpg", "image/jpeg", 2048L);
		fileAsset.setId("file-1");
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(eq("originals/travel/" + UUID_NAME + ".jpg"), eq(Duration.ofSeconds(300)),
			anyString())).thenReturn(Optional.of("https://r2.example/signed"));

		DownloadUrlRdo rdo = fileLogic.getDownloadUrl("file-1");

		ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
		verify(objectStorage).presignedGetUrl(anyString(), any(Duration.class), disposition.capture());
		assertThat(disposition.getValue()).startsWith("attachment;")
			.contains("filename*=UTF-8''%EC%A0%9C%EC%A3%BC%20%EC%97%AC%ED%96%89.jpg");
		assertThat(rdo.getUrl()).isEqualTo("https://r2.example/signed");
		assertThat(rdo.getFilename()).isEqualTo("제주 여행.jpg");
		assertThat(rdo.getSize()).isEqualTo(2048L);
		assertThat(rdo.getExpiresAt()).isNotNull();
	}

	@Test
	void getDownloadUrl_shouldServeRawAttachment() {
		FileAsset raw = FileAsset.pendingRaw("DSCF1234.RAF", UUID_NAME + ".raf", "image/x-fujifilm-raf", 83886080L, "u");
		raw.setId("raw-1");
		raw.markUploadCompleted();
		when(fileAssetStore.findById("raw-1")).thenReturn(raw);
		when(objectStorage.presignedGetUrl(eq("originals/travel/" + UUID_NAME + ".raf"), any(Duration.class),
			anyString())).thenReturn(Optional.of("https://r2.example/raw"));

		DownloadUrlRdo rdo = fileLogic.getDownloadUrl("raw-1");

		assertThat(rdo.getUrl()).isEqualTo("https://r2.example/raw");
		assertThat(rdo.getFilename()).isEqualTo("DSCF1234.RAF");
		assertThat(rdo.getSize()).isEqualTo(83886080L);
	}

	@Test
	void getDownloadUrl_shouldReturn501WhenStorageCannotPresign() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "a.jpg", UUID_NAME + ".jpg", "image/jpeg", 1L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);

		assertThatThrownBy(() -> fileLogic.getDownloadUrl("file-1"))
			.isInstanceOf(PresignedUrlNotSupportedException.class);
	}

	@Test
	void getImageFileById_shouldRefuseRawAttachmentAsNotFound() {
		FileAsset raw = FileAsset.pendingRaw("DSCF1234.RAF", UUID_NAME + ".raf", "image/x-fujifilm-raf", 1L, "u");
		raw.markUploadCompleted();
		when(fileAssetStore.findById("raw-1")).thenReturn(raw);

		assertThatThrownBy(() -> fileLogic.getImageFileById("raw-1", null))
			.isInstanceOf(RawFileNotViewableException.class)
			.extracting(e -> ((RawFileNotViewableException)e).getErrorCode().getHttpStatus().value())
			.isEqualTo(404);
		verifyNoInteractions(objectStorage);
	}
}
