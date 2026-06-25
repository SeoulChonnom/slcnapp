package com.seoulchonnom.spec.file.entity.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileType {
	LOGO("logo"),
	MAP("map"),
	TRAVEL("travel");

	private final String value;

	@JsonCreator
	public static FileType from(String value) {
		for (FileType fileType : values()) {
			if (fileType.value.equals(value)) {
				return fileType;
			}
		}
		throw new IllegalArgumentException("Unknown file type: " + value);
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
