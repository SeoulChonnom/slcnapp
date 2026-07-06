package com.seoulchonnom.aggregate.filebox.store.doc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "file_box")
@CompoundIndex(name = "uk_file_box_owner", def = "{'ownerType': 1, 'ownerId': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileBoxDoc {
	@Id
	private String id;
	@Indexed
	private FileBoxOwnerType ownerType;
	@Indexed
	private String ownerId;
	private List<FileBoxItem> items = new ArrayList<>();
	private long registeredTime;
	private long modifiedTime;
}
