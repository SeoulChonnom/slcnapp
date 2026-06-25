package com.seoulchonnom.aggregate.file.util;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileUtilsTest {
	private static final byte[] PNG_BYTES = Base64.getDecoder()
		.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");

	private final FileUtils fileUtils = new FileUtils();

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(tempDir.resolve("logo"));
		ReflectionTestUtils.setField(fileUtils, "directory", tempDir + "/");
	}

	@Test
	void saveImages_shouldAcceptUppercaseImageExtensionWhenContentIsValid() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "image.PNG", "image/png", PNG_BYTES);

		FileReference fileReference = fileUtils.saveImages(file, "logo");

		assertThat(fileReference.getType()).isEqualTo(FileType.LOGO);
		assertThat(fileReference.getFilename()).endsWith(".png");
	}

	@Test
	void saveImageAsset_shouldCreateTravelDirectoryAndReturnFileAsset() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "travel.PNG", "image/png", PNG_BYTES);

		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		assertThat(fileAsset.getType()).isEqualTo(FileType.TRAVEL);
		assertThat(fileAsset.getOriginalFilename()).isEqualTo("travel.PNG");
		assertThat(fileAsset.getStoredFilename()).endsWith(".png");
		assertThat(fileAsset.getPath()).isEqualTo("travel/" + fileAsset.getStoredFilename());
		assertThat(Files.exists(tempDir.resolve(fileAsset.getPath()))).isTrue();
	}

	@Test
	void saveImages_shouldRejectFileWhenContentTypeIsNotImage() {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "text/plain", PNG_BYTES);

		assertThatThrownBy(() -> fileUtils.saveImages(file, "logo"))
			.isInstanceOf(FileExtException.class);
	}

	@Test
	void saveImages_shouldRejectFileWhenBytesAreNotImage() {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "not-image".getBytes());

		assertThatThrownBy(() -> fileUtils.saveImages(file, "logo"))
			.isInstanceOf(FileExtException.class);
	}
}
