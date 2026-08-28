package com.seoulchonnom.spec.file.facade.sdo;

import java.util.ArrayList;
import java.util.List;

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
	/**
	 * 원본 픽셀 크기. 파생본도 종횡비가 같으므로 클라이언트는 이 값으로 레이아웃을 미리 잡을 수 있다. 과거 자산은 0이다.
	 */
	private int width;
	private int height;
	/**
	 * 이 자산에 대해 실제로 사용할 수 있는 파생본 이름 목록.
	 */
	private List<String> variants = new ArrayList<>();

	public FileAssetRdo(String fileId, FileType type, String originalFilename, String filename, String path,
		String mimeType, long size) {
		this(fileId, type, originalFilename, filename, path, mimeType, size, 0, 0, new ArrayList<>());
	}

	public static FileAssetRdo from(FileAsset fileAsset) {
		return new FileAssetRdo(
			fileAsset.getId(),
			fileAsset.getType(),
			fileAsset.getOriginalFilename(),
			fileAsset.getStoredFilename(),
			fileAsset.getPath(),
			fileAsset.getMimeType(),
			fileAsset.getSize(),
			fileAsset.getWidth(),
			fileAsset.getHeight(),
			new ArrayList<>(fileAsset.variantNames())
		);
	}
}
