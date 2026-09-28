package com.seoulchonnom.spec.file.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
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
	/**
	 * RAW는 다운로드 전용이라 인라인 조회가 막혀 있다. 클라이언트는 이 값으로 표시 여부를 가른다.
	 */
	private FileKind kind = FileKind.IMAGE;
	/**
	 * RAW 직접 업로드가 완료 검증을 통과하기 전이면 PENDING이다. 여행에는 READY만 연결할 수 있다.
	 */
	private FileStatus status = FileStatus.READY;

	public FileAssetRdo(String fileId, FileType type, String originalFilename, String filename, String path,
		String mimeType, long size) {
		this(fileId, type, originalFilename, filename, path, mimeType, size, 0, 0, new ArrayList<>(), FileKind.IMAGE,
			FileStatus.READY);
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
			new ArrayList<>(fileAsset.variantNames()),
			fileAsset.getKind(),
			fileAsset.getStatus()
		);
	}
}
