package com.seoulchonnom.rest.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

class FileResourceTest {
	@Test
	void uploadFile_shouldReturnSavedPath() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", new byte[] { 1, 2, 3 });
		when(fileLogic.uploadFile(file, "images")).thenReturn("/images/sample.png");

		var response = fileResource.uploadFile(file, "images");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("/images/sample.png", response.getBody());
	}

	@Test
	void getFile_shouldReturnBinaryResponseWithMimeType() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		byte[] image = new byte[] { 1, 2, 3 };
		when(fileLogic.getImageFile("/images/sample.png"))
			.thenReturn(ImageFileRdo.builder().image(image).mimeType("image/png").build());

		var response = fileResource.getFile("/images/sample.png");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/png", response.getHeaders().getContentType().toString());
		assertEquals(3, response.getHeaders().getContentLength());
		assertArrayEquals(image, response.getBody());
	}
}
