package com.seoulchonnom.spec.file.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
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
	}
}
