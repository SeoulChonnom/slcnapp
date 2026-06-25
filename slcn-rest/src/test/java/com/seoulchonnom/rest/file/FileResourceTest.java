package com.seoulchonnom.rest.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

class FileResourceTest {
	@Test
	void uploadFile_shouldReturnFileAsset() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", new byte[] { 1, 2, 3 });
		FileAsset fileAsset = new FileAsset(FileType.LOGO, "sample.png",
			"72d768d4-2b05-48f9-bee8-fee3b52e909f.png", "image/png", 3L);
		fileAsset.setId("file-1");
		when(fileLogic.uploadFile(file, "logo")).thenReturn(fileAsset);

		var response = fileResource.uploadFile(file, "logo");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("file-1", response.getBody().getFileId());
		assertEquals(FileType.LOGO, response.getBody().getType());
		assertEquals("72d768d4-2b05-48f9-bee8-fee3b52e909f.png", response.getBody().getFilename());
	}

	@Test
	void uploadFiles_shouldReturnFileAssets() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1, 2, 3});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png",
			"72d768d4-2b05-48f9-bee8-fee3b52e909f.png", "image/png", 3L);
		fileAsset.setId("file-1");
		when(fileLogic.uploadFiles(List.of(file), "travel")).thenReturn(List.of(fileAsset));

		var response = fileResource.uploadFiles(List.of(file), "travel");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("file-1", response.getBody().get(0).getFileId());
		assertEquals(FileType.TRAVEL, response.getBody().get(0).getType());
		assertEquals("travel/72d768d4-2b05-48f9-bee8-fee3b52e909f.png", response.getBody().get(0).getPath());
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

	@Test
	void getFileById_shouldReturnBinaryResponseWithMimeType() {
		FileLogic fileLogic = mock(FileLogic.class);
		FileResource fileResource = new FileResource(fileLogic);
		byte[] image = new byte[] {1, 2, 3};
		when(fileLogic.getImageFileById("file-1"))
			.thenReturn(ImageFileRdo.builder().image(image).mimeType("image/png").build());

		var response = fileResource.getFileById("file-1");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/png", response.getHeaders().getContentType().toString());
		assertEquals(3, response.getHeaders().getContentLength());
		assertArrayEquals(image, response.getBody());
	}
}
