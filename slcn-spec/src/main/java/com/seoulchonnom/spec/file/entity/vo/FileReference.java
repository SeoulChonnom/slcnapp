package com.seoulchonnom.spec.file.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileReference {
	private FileType type;
	private String filename;

	public static FileReference fromPath(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		String[] parts = path.split("/", 2);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("Invalid file reference path: " + path);
		}
		return new FileReference(FileType.from(parts[0]), parts[1]);
	}

	public String toPath() {
		return type.getValue() + '/' + filename;
	}
}
