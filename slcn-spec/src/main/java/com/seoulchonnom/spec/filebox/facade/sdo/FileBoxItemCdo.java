package com.seoulchonnom.spec.filebox.facade.sdo;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileBoxItemCdo {
	private String fileAssetId;
	/**
	 * 같은 사진의 RAW 다운로드 첨부. 여행 앨범에서만 쓰고, 없으면 null이다.
	 */
	private String rawFileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private Integer sortOrder;
}
