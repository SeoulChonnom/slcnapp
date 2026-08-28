package com.seoulchonnom.aggregate.file.store.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.file.store.doc.FileAssetDoc;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

@Component
public class FileAssetDocMapper {
	public FileAssetDoc toDoc(FileAsset fileAsset) {
		return new FileAssetDoc(
			fileAsset.getId(),
			fileAsset.getType(),
			fileAsset.getOriginalFilename(),
			fileAsset.getStoredFilename(),
			fileAsset.getPath(),
			fileAsset.getMimeType(),
			fileAsset.getSize(),
			fileAsset.getWidth(),
			fileAsset.getHeight(),
			new ArrayList<>(fileAsset.getVariants()),
			fileAsset.getRegisteredTime(),
			fileAsset.getModifiedTime()
		);
	}

	public FileAsset toDomain(FileAssetDoc doc) {
		FileAsset fileAsset = FileAsset.builder()
			.type(doc.getType())
			.originalFilename(doc.getOriginalFilename())
			.storedFilename(doc.getStoredFilename())
			.path(doc.getPath())
			.mimeType(doc.getMimeType())
			.size(doc.getSize())
			.width(doc.getWidth())
			.height(doc.getHeight())
			.variants(variantsOf(doc))
			.build();
		fileAsset.setId(doc.getId());
		fileAsset.setRegisteredTime(doc.getRegisteredTime());
		fileAsset.setModifiedTime(doc.getModifiedTime());
		return fileAsset;
	}

	private List<FileVariant> variantsOf(FileAssetDoc doc) {
		return doc.getVariants() == null ? new ArrayList<>() : new ArrayList<>(doc.getVariants());
	}
}
