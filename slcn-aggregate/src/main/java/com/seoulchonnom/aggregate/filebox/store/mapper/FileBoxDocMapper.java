package com.seoulchonnom.aggregate.filebox.store.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.filebox.store.doc.FileBoxDoc;
import com.seoulchonnom.spec.filebox.entity.FileBox;

@Component
public class FileBoxDocMapper {
	public FileBoxDoc toDoc(FileBox fileBox) {
		return new FileBoxDoc(
			fileBox.getId(),
			fileBox.getOwnerType(),
			fileBox.getOwnerId(),
			fileBox.getItems() == null ? new ArrayList<>() : new ArrayList<>(fileBox.getItems()),
			fileBox.getRegisteredTime(),
			fileBox.getModifiedTime()
		);
	}

	public FileBox toDomain(FileBoxDoc doc) {
		FileBox fileBox = FileBox.builder()
			.ownerType(doc.getOwnerType())
			.ownerId(doc.getOwnerId())
			.items(doc.getItems() == null ? new ArrayList<>() : new ArrayList<>(doc.getItems()))
			.build();
		fileBox.setId(doc.getId());
		fileBox.setRegisteredTime(doc.getRegisteredTime());
		fileBox.setModifiedTime(doc.getModifiedTime());
		return fileBox;
	}
}
