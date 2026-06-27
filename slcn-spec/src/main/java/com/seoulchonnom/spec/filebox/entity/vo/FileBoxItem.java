package com.seoulchonnom.spec.filebox.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

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
public class FileBoxItem implements JsonSerializable {
	private String id;
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private int sortOrder;
}
