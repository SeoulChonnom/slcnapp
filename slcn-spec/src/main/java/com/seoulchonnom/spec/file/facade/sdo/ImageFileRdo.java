package com.seoulchonnom.spec.file.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFileRdo {
	private byte[] image;
	private String mimeType;
}