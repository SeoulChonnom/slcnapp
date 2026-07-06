package com.seoulchonnom.spec.filebox.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
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
public class FileBoxItemRdo {
	private String id;
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private int sortOrder;
	private FileAssetRdo file;
}
