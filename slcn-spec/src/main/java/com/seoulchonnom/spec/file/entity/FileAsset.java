package com.seoulchonnom.spec.file.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class FileAsset extends DomainEntity {
	private FileType type;
	private String originalFilename;
	private String storedFilename;
	private String path;
	private String mimeType;
	private long size;

	public FileAsset(FileType type, String originalFilename, String storedFilename, String mimeType, long size) {
		super();
		this.type = type;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.path = type.getValue() + "/" + storedFilename;
		this.mimeType = mimeType;
		this.size = size;
	}

	public FileReference toFileReference() {
		return new FileReference(type, storedFilename);
	}
}
