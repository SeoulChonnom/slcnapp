package com.seoulchonnom.aggregate.file.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.file.store.doc.FileAssetDoc;
import com.seoulchonnom.spec.file.entity.FileAsset;

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
			.build();
		fileAsset.setId(doc.getId());
		fileAsset.setRegisteredTime(doc.getRegisteredTime());
		fileAsset.setModifiedTime(doc.getModifiedTime());
		return fileAsset;
	}
}
