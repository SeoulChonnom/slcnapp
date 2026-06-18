package com.seoulchonnom.rest.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

class FileResourceTest {
	@Test
	void uploadFile_shouldReturnFileRef() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", new byte[] { 1, 2, 3 });
		when(fileLogic.uploadFile(file, "logo"))
			.thenReturn(new FileReference(FileType.LOGO, "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"));

		var response = fileResource.uploadFile(file, "logo");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(FileType.LOGO, response.getBody().getType());
		assertEquals("72d768d4-2b05-48f9-bee8-fee3b52e909f.png", response.getBody().getFilename());
	}

	@Test
	void getFile_shouldReturnBinaryResponseWithMimeType() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		byte[] image = new byte[] { 1, 2, 3 };
		when(fileLogic.getImageFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.thenReturn(ImageFileRdo.builder().image(image).mimeType("image/png").build());

		var response = fileResource.getFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/png", response.getHeaders().getContentType().toString());
		assertEquals(3, response.getHeaders().getContentLength());
		assertArrayEquals(image, response.getBody());
	}
}
