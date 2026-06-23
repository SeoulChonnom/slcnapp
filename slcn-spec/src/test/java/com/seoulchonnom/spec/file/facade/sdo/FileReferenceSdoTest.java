package com.seoulchonnom.spec.file.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileReferenceSdoTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void fileRefSdo_shouldSerializeFileTypeAsLowercaseValue() throws Exception {
		FileReferenceSdo fileReferenceSdo = new FileReferenceSdo(FileType.LOGO, "72d768d4-2b05-48f9-bee8-fee3b52e909f.png");

		String json = objectMapper.writeValueAsString(fileReferenceSdo);

		assertThat(json).contains("\"type\":\"logo\"");
	}

	@Test
	void fileRefSdo_shouldDeserializeLowercaseFileType() throws Exception {
		String json = "{\"type\":\"map\",\"filename\":\"11111111-2222-4333-8888-aaaaaaaaaaaa.png\"}";

		FileReferenceSdo fileReferenceSdo = objectMapper.readValue(json, FileReferenceSdo.class);

		assertThat(fileReferenceSdo.getType()).isEqualTo(FileType.MAP);
		assertThat(fileReferenceSdo.getFilename()).isEqualTo("11111111-2222-4333-8888-aaaaaaaaaaaa.png");
	}

	@Test
	void fileReferenceFromPath_shouldRejectInvalidPathShape() {
		assertThatThrownBy(() -> FileReference.fromPath("logo"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Invalid file reference path: logo");
	}
}
