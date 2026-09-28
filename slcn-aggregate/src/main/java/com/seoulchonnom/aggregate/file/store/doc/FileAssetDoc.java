package com.seoulchonnom.aggregate.file.store.doc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;
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
	/**
	 * 이 필드가 생기기 전 문서에는 값이 없다. 도메인 getter가 IMAGE/READY로 읽는다.
	 */
	private FileKind kind;
	private FileStatus status;
	private String uploadId;
	private long registeredTime;
	private long modifiedTime;
}
