package com.seoulchonnom.spec.file.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileRefSdoTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void fileRefSdo_shouldSerializeFileTypeAsLowercaseValue() throws Exception {
		FileRefSdo fileRefSdo = new FileRefSdo(FileType.LOGO, "72d768d4-2b05-48f9-bee8-fee3b52e909f.png");

		String json = objectMapper.writeValueAsString(fileRefSdo);

		assertThat(json).contains("\"type\":\"logo\"");
	}

	@Test
	void fileRefSdo_shouldDeserializeLowercaseFileType() throws Exception {
		String json = "{\"type\":\"map\",\"filename\":\"11111111-2222-4333-8888-aaaaaaaaaaaa.png\"}";

		FileRefSdo fileRefSdo = objectMapper.readValue(json, FileRefSdo.class);

		assertThat(fileRefSdo.getType()).isEqualTo(FileType.MAP);
		assertThat(fileRefSdo.getFilename()).isEqualTo("11111111-2222-4333-8888-aaaaaaaaaaaa.png");
	}
}
