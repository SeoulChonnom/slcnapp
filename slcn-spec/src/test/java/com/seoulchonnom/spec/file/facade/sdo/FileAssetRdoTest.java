package com.seoulchonnom.spec.file.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileAssetRdoTest {
	@Test
	void fileReferenceFromPath_shouldRejectInvalidPathShape() {
		assertThatThrownBy(() -> FileReference.fromPath("logo"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Invalid file reference path: logo");
	}

	@Test
	void fileAssetRdo_shouldExposeFileIdAndPath() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png",
			"72d768d4-2b05-48f9-bee8-fee3b52e909f.png", "image/png", 3L);
		fileAsset.setId("file-1");

		FileAssetRdo fileAssetRdo = FileAssetRdo.from(fileAsset);

		assertThat(fileAssetRdo.getFileId()).isEqualTo("file-1");
		assertThat(fileAssetRdo.getType()).isEqualTo(FileType.TRAVEL);
		assertThat(fileAssetRdo.getPath()).isEqualTo("travel/72d768d4-2b05-48f9-bee8-fee3b52e909f.png");
		assertThat(fileAssetRdo.getKind()).isEqualTo(FileKind.IMAGE);
		assertThat(fileAssetRdo.getStatus()).isEqualTo(FileStatus.READY);
	}

	@Test
	void fileAssetRdo_shouldExposeRawKindAndPendingStatus() {
		FileAsset fileAsset = FileAsset.builder()
			.type(FileType.TRAVEL)
			.originalFilename("DSCF1234.RAF")
			.storedFilename("72d768d4-2b05-48f9-bee8-fee3b52e909f.raf")
			.kind(FileKind.RAW)
			.status(FileStatus.PENDING)
			.uploadId("upload-1")
			.build();

		FileAssetRdo fileAssetRdo = FileAssetRdo.from(fileAsset);

		assertThat(fileAssetRdo.getKind()).isEqualTo(FileKind.RAW);
		assertThat(fileAssetRdo.getStatus()).isEqualTo(FileStatus.PENDING);
	}

	@Test
	void legacyConstructor_shouldDefaultToReadyImage() {
		FileAssetRdo fileAssetRdo = new FileAssetRdo("file-1", FileType.PROFILE, "p.png", "stored.png",
			"profile/stored.png", "image/png", 10L);

		assertThat(fileAssetRdo.getKind()).isEqualTo(FileKind.IMAGE);
		assertThat(fileAssetRdo.getStatus()).isEqualTo(FileStatus.READY);
	}
}
