package com.seoulchonnom.spec.filebox.entity;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;

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
public class FileBox extends DomainEntity {
	private FileBoxOwnerType ownerType;
	private String ownerId;
	@Builder.Default
	private List<FileBoxItem> items = new ArrayList<>();
}
