package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.file.util.FileUtils;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileLogicTest {
	private final FileUtils fileUtils = mock(FileUtils.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final FileLogic fileLogic = new FileLogic(fileUtils, fileAssetStore);

	@TempDir
	Path tempDir;

	@Test
	void uploadFiles_shouldSaveAssetsAndReturnStoredMetadata() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		when(fileUtils.saveImageAsset(file, "travel")).thenReturn(fileAsset);
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		List<FileAsset> result = fileLogic.uploadFiles(List.of(file), "travel");

		assertThat(result).containsExactly(fileAsset);
		verify(fileAssetStore).save(fileAsset);
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
		verify(fileUtils).isValidFileRef("travel", "stored.png");
	}
}
