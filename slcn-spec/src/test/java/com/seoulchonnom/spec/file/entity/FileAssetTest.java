package com.seoulchonnom.spec.file.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileAssetTest {
	@Test
	void getKind_shouldDefaultToImageWhenMissing() {
		FileAsset fileAsset = FileAsset.builder().type(FileType.TRAVEL).build();

		assertThat(fileAsset.getKind()).isEqualTo(FileKind.IMAGE);
	}

	@Test
	void getStatus_shouldDefaultToReadyWhenMissing() {
		FileAsset fileAsset = FileAsset.builder().type(FileType.TRAVEL).build();

		assertThat(fileAsset.getStatus()).isEqualTo(FileStatus.READY);
	}

	@Test
	void uploadConstructor_shouldCreateReadyImageWithoutUploadId() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "photo.jpg",
			"72d768d4-2b05-48f9-bee8-fee3b52e909f.jpg", "image/jpeg", 3L);

		assertThat(fileAsset.getKind()).isEqualTo(FileKind.IMAGE);
		assertThat(fileAsset.getStatus()).isEqualTo(FileStatus.READY);
		assertThat(fileAsset.getUploadId()).isNull();
	}

	@Test
	void getKind_shouldKeepExplicitRawPendingValues() {
		FileAsset fileAsset = FileAsset.builder()
			.type(FileType.TRAVEL)
			.kind(FileKind.RAW)
			.status(FileStatus.PENDING)
			.uploadId("upload-1")
			.build();

		assertThat(fileAsset.getKind()).isEqualTo(FileKind.RAW);
		assertThat(fileAsset.getStatus()).isEqualTo(FileStatus.PENDING);
		assertThat(fileAsset.getUploadId()).isEqualTo("upload-1");
	}
}
