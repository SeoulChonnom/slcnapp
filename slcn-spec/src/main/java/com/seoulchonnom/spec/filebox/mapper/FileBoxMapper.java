package com.seoulchonnom.spec.filebox.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;

@Component
public class FileBoxMapper {
	public FileBoxItemRdo toFileBoxItemRdo(FileBoxItem item, FileAssetRdo file) {
		FileBoxItemRdo rdo = new FileBoxItemRdo();
		rdo.setId(item.getId());
		rdo.setFileAssetId(item.getFileAssetId());
		rdo.setTargetType(item.getTargetType());
		rdo.setTargetId(item.getTargetId());
		rdo.setRole(item.getRole());
		rdo.setCaption(item.getCaption());
		rdo.setSortOrder(item.getSortOrder());
		rdo.setFile(file);
		return rdo;
	}

	public FileBoxItem toFileBoxItem(FileBoxItemCdo cdo) {
		FileBoxItem item = new FileBoxItem();
		item.setFileAssetId(cdo.getFileAssetId());
		item.setTargetType(cdo.getTargetType());
		item.setTargetId(cdo.getTargetId());
		item.setRole(cdo.getRole());
		item.setCaption(cdo.getCaption());
		item.setSortOrder(cdo.getSortOrder() == null ? 0 : cdo.getSortOrder());
		return item;
	}

	public FileBoxItem toFileBoxItem(FileBoxItemUdo udo) {
		FileBoxItem item = new FileBoxItem();
		item.setId(udo.getId());
		item.setFileAssetId(udo.getFileAssetId());
		item.setTargetType(udo.getTargetType());
		item.setTargetId(udo.getTargetId());
		item.setRole(udo.getRole());
		item.setCaption(udo.getCaption());
		item.setSortOrder(udo.getSortOrder() == null ? 0 : udo.getSortOrder());
		return item;
	}
}
