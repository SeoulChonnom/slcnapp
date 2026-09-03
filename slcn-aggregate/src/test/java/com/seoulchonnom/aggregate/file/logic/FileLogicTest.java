package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FileUploadException;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;

class FileLogicTest {
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

	@Test
	void getImageFileById_shouldReadFileUsingStoredAsset() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1, 2, 3});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
		verify(fileUtils).isValidFileRef("travel", "stored.png");
	}

	@Test
	void getImageFileById_shouldReadVariantWhenGeneratedAndPresentOnDisk() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored_home-thumb.webp"), new byte[] {9, 9});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(fileUtils.existsFileRef("travel", "stored_home-thumb.webp")).thenReturn(true);

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(9, 9);
		assertThat(result.getMimeType()).isEqualTo("image/webp");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantWasNeverGenerated() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1, 2, 3});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldServeJpegVariantWhenThatIsWhatWasRecorded() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored_home-thumb.jpg"), new byte[] {8});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.jpg", "image/jpeg")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(fileUtils.existsFileRef("travel", "stored_home-thumb.jpg")).thenReturn(true);

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getMimeType()).isEqualTo("image/jpeg");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantIsRecordedButMissingOnDisk() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1, 2, 3});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(fileUtils.existsFileRef("travel", "stored_home-thumb.webp")).thenReturn(false);

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldCarryTheUploadedFilenameForDownloads() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "제주 바다.png", "stored.png", "image/png", 1L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("제주 바다.png");
	}

	@Test
	void getImageFileById_shouldNameVariantDownloadsAfterTheOriginalFile() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored_home-thumb.webp"), new byte[] {9});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "제주 바다.png", "stored.png", "image/png", 1L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(fileUtils.existsFileRef("travel", "stored_home-thumb.webp")).thenReturn(true);

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getDownloadFilename()).isEqualTo("제주 바다_home-thumb.webp");
	}

	@Test
	void getImageFileById_shouldStripPathAndControlCharactersFromTheUploadedFilename() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1});
		ReflectionTestUtils.setField(fileLogic, "directory", tempDir + "/");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "../../etc/pa\"ss\r\nwd.png", "stored.png", "image/png", 1L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("passwd.png");
	}
}
