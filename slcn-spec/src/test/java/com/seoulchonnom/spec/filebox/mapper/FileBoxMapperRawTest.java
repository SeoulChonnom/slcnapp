package com.seoulchonnom.spec.filebox.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;

class FileBoxMapperRawTest {
	private final FileBoxMapper mapper = new FileBoxMapper();

	@Test
	void toFileBoxItem_shouldCopyRawFileAssetIdFromCdoAndUdo() {
		FileBoxItemCdo cdo = new FileBoxItemCdo("photo-1", "raw-1", FileBoxTargetType.TRAVEL, null,
			FileBoxItemRole.GALLERY, null, 1);
		FileBoxItemUdo udo = new FileBoxItemUdo("item-1", "photo-1", "raw-1", FileBoxTargetType.TRAVEL, null,
			FileBoxItemRole.GALLERY, null, 1);

		assertThat(mapper.toFileBoxItem(cdo).getRawFileAssetId()).isEqualTo("raw-1");
		assertThat(mapper.toFileBoxItem(udo).getRawFileAssetId()).isEqualTo("raw-1");
	}

	@Test
	void toFileBoxItemRdo_shouldExposeRawIdAndResolvedRawFile() {
		FileBoxItem item = FileBoxItem.builder()
			.id("item-1")
			.fileAssetId("photo-1")
			.rawFileAssetId("raw-1")
			.targetType(FileBoxTargetType.TRAVEL)
			.role(FileBoxItemRole.GALLERY)
			.build();
		FileAssetRdo file = new FileAssetRdo("photo-1", FileType.TRAVEL, "a.jpg", "a.jpg", "travel/a.jpg",
			"image/jpeg", 1L);
		FileAssetRdo rawFile = new FileAssetRdo("raw-1", FileType.TRAVEL, "DSCF1234.RAF", "a.raf", "travel/a.raf",
			"image/x-fujifilm-raf", 83886080L);

		FileBoxItemRdo rdo = mapper.toFileBoxItemRdo(item, file, rawFile);

		assertThat(rdo.getRawFileAssetId()).isEqualTo("raw-1");
		assertThat(rdo.getRawFile()).isSameAs(rawFile);
		assertThat(mapper.toFileBoxItemRdo(item, file).getRawFile()).isNull();
	}
}
