package com.seoulchonnom.spec.file.facade.sdo;

import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileAssetRdo {
	private String fileId;
	private FileType type;
	private String originalFilename;
	private String filename;
	private String path;
	private String mimeType;
	private long size;

	public static FileAssetRdo from(FileAsset fileAsset) {
		return new FileAssetRdo(
			fileAsset.getId(),
			fileAsset.getType(),
			fileAsset.getOriginalFilename(),
			fileAsset.getStoredFilename(),
			fileAsset.getPath(),
			fileAsset.getMimeType(),
			fileAsset.getSize()
		);
	}
}
