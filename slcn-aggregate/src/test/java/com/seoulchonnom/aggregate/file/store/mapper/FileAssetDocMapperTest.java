package com.seoulchonnom.aggregate.file.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.file.store.doc.FileAssetDoc;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

class FileAssetDocMapperTest {
	private final FileAssetDocMapper mapper = new FileAssetDocMapper();

	@Test
	void toDocAndBack_shouldPreserveRawUploadFields() {
		FileAsset fileAsset = FileAsset.builder()
			.type(FileType.TRAVEL)
			.originalFilename("DSCF1234.RAF")
			.storedFilename("72d768d4-2b05-48f9-bee8-fee3b52e909f.raf")
			.path("travel/72d768d4-2b05-48f9-bee8-fee3b52e909f.raf")
			.mimeType("image/x-fujifilm-raf")
			.size(83886080L)
			.kind(FileKind.RAW)
			.status(FileStatus.PENDING)
			.uploadId("upload-1")
			.build();
		fileAsset.setId("file-1");

		FileAssetDoc doc = mapper.toDoc(fileAsset);
		FileAsset restored = mapper.toDomain(doc);

		assertThat(doc.getKind()).isEqualTo(FileKind.RAW);
		assertThat(doc.getStatus()).isEqualTo(FileStatus.PENDING);
		assertThat(doc.getUploadId()).isEqualTo("upload-1");
		assertThat(restored.getId()).isEqualTo("file-1");
		assertThat(restored.getKind()).isEqualTo(FileKind.RAW);
		assertThat(restored.getStatus()).isEqualTo(FileStatus.PENDING);
		assertThat(restored.getUploadId()).isEqualTo("upload-1");
		assertThat(restored.getSize()).isEqualTo(83886080L);
	}

	@Test
	void toDomain_shouldReadLegacyDocWithoutKindAsReadyImage() {
		FileAssetDoc legacy = new FileAssetDoc();
		legacy.setId("file-legacy");
		legacy.setType(FileType.TRAVEL);
		legacy.setStoredFilename("72d768d4-2b05-48f9-bee8-fee3b52e909f.jpg");
		legacy.setVariants(List.of(new FileVariant("home-thumb", "x_home-thumb.webp", "image/webp")));

		FileAsset restored = mapper.toDomain(legacy);

		assertThat(restored.getKind()).isEqualTo(FileKind.IMAGE);
		assertThat(restored.getStatus()).isEqualTo(FileStatus.READY);
		assertThat(restored.getUploadId()).isNull();
		assertThat(restored.variantNames()).containsExactly("home-thumb");
	}
}
