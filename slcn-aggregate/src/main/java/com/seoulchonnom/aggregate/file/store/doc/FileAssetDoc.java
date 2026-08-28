package com.seoulchonnom.aggregate.file.store.doc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "file_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileAssetDoc {
	@Id
	private String id;
	@Indexed
	private FileType type;
	private String originalFilename;
	private String storedFilename;
	@Indexed(unique = true)
	private String path;
	private String mimeType;
	private long size;
	private int width;
	private int height;
	private List<FileVariant> variants = new ArrayList<>();
	private long registeredTime;
	private long modifiedTime;
}
