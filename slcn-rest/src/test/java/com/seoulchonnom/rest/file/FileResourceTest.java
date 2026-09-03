package com.seoulchonnom.rest.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

class FileResourceTest {
	private final FileLogic fileLogic = mock(FileLogic.class);
	private final FileResource fileResource = new FileResource(fileLogic);

	@Test
	void uploadFile_shouldReturnFileAsset() {
		MockMultipartFile file = new MockMultipartFile("file", "sample.png", "image/png", new byte[] {1, 2, 3});
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
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1, 2, 3});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png",
			"72d768d4-2b05-48f9-bee8-fee3b52e909f.png", "image/png", 3L);
		fileAsset.setId("file-1");
		fileAsset.setWidth(1600);
		fileAsset.setHeight(900);
		fileAsset.setVariants(List.of(
			new FileVariant("home-feature", "cover_home-feature.webp", "image/webp"),
			new FileVariant("home-thumb", "cover_home-thumb.webp", "image/webp")));
		when(fileLogic.uploadFiles(List.of(file), "travel")).thenReturn(List.of(fileAsset));

		var response = fileResource.uploadFiles(List.of(file), "travel");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("file-1", response.getBody().get(0).getFileId());
		assertEquals(FileType.TRAVEL, response.getBody().get(0).getType());
		assertEquals("travel/72d768d4-2b05-48f9-bee8-fee3b52e909f.png", response.getBody().get(0).getPath());
		assertEquals(1600, response.getBody().get(0).getWidth());
		assertEquals(900, response.getBody().get(0).getHeight());
		assertEquals(List.of("home-feature", "home-thumb"), response.getBody().get(0).getVariants());
	}

	@Test
	void getFile_shouldReturnBinaryResponseWithMimeType() {
		byte[] image = new byte[] {1, 2, 3};
		when(fileLogic.getImageFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.thenReturn(imageRdo(image, "image/png", "original"));

		var response = fileResource.getFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/png", response.getHeaders().getContentType().toString());
		assertEquals(3, response.getHeaders().getContentLength());
		assertArrayEquals(image, response.getBody());
	}

	@Test
	void getFileById_shouldReturnBinaryResponseWithMimeType() {
		byte[] image = new byte[] {1, 2, 3};
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(imageRdo(image, "image/png", "original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/png", response.getHeaders().getContentType().toString());
		assertEquals(3, response.getHeaders().getContentLength());
		assertArrayEquals(image, response.getBody());
	}

	@Test
	void getFileById_shouldAllowPrivateBrowserCachingWithEtag() {
		when(fileLogic.getImageFileById("file-1", null))
			.thenReturn(imageRdo(new byte[] {1}, "image/png", "original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertEquals("\"file-1-original\"", response.getHeaders().getETag());
		assertEquals("max-age=86400, private", response.getHeaders().getCacheControl());
	}

	@Test
	void getFileById_shouldServeRequestedVariantAndTagItSeparately() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/webp", "home-thumb"));

		var response = fileResource.getFileById("file-1", "home-thumb", 320, "webp", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/webp", response.getHeaders().getContentType().toString());
		assertEquals("\"file-1-home-thumb\"", response.getHeaders().getETag());
	}

	@Test
	void getFileById_shouldReflectJpegContentTypeWhenTheVariantFellBack() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/jpeg", "home-thumb"));

		var response = fileResource.getFileById("file-1", "home-thumb", null, null, null);

		assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
		assertEquals("\"file-1-home-thumb\"", response.getHeaders().getETag());
	}

	@Test
	void getFileById_shouldSelectVariantByWidthWhenVariantIsAbsent() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_FEATURE))
			.thenReturn(imageRdo(new byte[] {7}, "image/jpeg", "home-feature"));

		var response = fileResource.getFileById("file-1", null, 640, null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("\"file-1-home-feature\"", response.getHeaders().getETag());
	}

	@Test
	void getFileById_shouldServeDefaultVariantForUnknownVariantInsteadOfTheOriginal() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_FEATURE))
			.thenReturn(imageRdo(new byte[] {5}, "image/webp", "home-feature"));

		var response = fileResource.getFileById("file-1", "does-not-exist", null, null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("\"file-1-home-feature\"", response.getHeaders().getETag());
		verify(fileLogic, never()).getImageFileById("file-1", null);
	}

	@Test
	void getFileById_shouldServeOriginalWhenVariantIsExplicitlyOriginal() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(imageRdo(new byte[] {1}, "image/png", "original"));

		var response = fileResource.getFileById("file-1", "original", null, null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("\"file-1-original\"", response.getHeaders().getETag());
	}

	@Test
	void getFileById_shouldStillServeOriginalWhenNoParametersAreGiven() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(imageRdo(new byte[] {1}, "image/png", "original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("\"file-1-original\"", response.getHeaders().getETag());
	}

	@Test
	void downloadFileById_shouldReturnOriginalAsAttachmentWithUploadedFilename() {
		when(fileLogic.downloadImageFileById("file-1", null))
			.thenReturn(imageRdo(new byte[] {1, 2}, "image/png", "original", "여행 사진.png"));

		var response = fileResource.downloadFileById("file-1", null, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertTrue(disposition.startsWith("attachment;"), disposition);
		// 한글 파일명은 RFC 5987로 인코딩되어야 한다.
		assertTrue(disposition.contains("filename*=UTF-8''"), disposition);
		assertArrayEquals(new byte[] {1, 2}, response.getBody());
	}

	@Test
	void downloadFileById_shouldReturnRequestedVariantWhenItIsKnown() {
		when(fileLogic.downloadImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/webp", "home-thumb", "cover_home-thumb.webp"));

		var response = fileResource.downloadFileById("file-1", "home-thumb", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("image/webp", response.getHeaders().getContentType().toString());
		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
			.contains("cover_home-thumb.webp"));
	}

	@Test
	void downloadFileById_shouldFallBackToOriginalForUnknownVariantSoSavesAreNeverDownscaled() {
		when(fileLogic.downloadImageFileById("file-1", null))
			.thenReturn(imageRdo(new byte[] {1}, "image/png", "original", "cover.png"));

		var response = fileResource.downloadFileById("file-1", "does-not-exist", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(fileLogic, never()).downloadImageFileById("file-1", ImageVariant.HOME_FEATURE);
	}

	@Test
	void downloadFileById_shouldNotShareItsEtagWithTheInlineResponse() {
		when(fileLogic.downloadImageFileById("file-1", null))
			.thenReturn(imageRdo(new byte[] {1}, "image/png", "original", "cover.png"));

		var response = fileResource.downloadFileById("file-1", null, null);

		assertEquals("\"file-1-original-download\"", response.getHeaders().getETag());
	}

	@Test
	void downloadFileById_shouldReturnNotModifiedWhenEtagMatches() {
		var response = fileResource.downloadFileById("file-1", null, "\"file-1-original-download\"");

		assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
		verify(fileLogic, never()).downloadImageFileById(anyString(), any());
	}

	@Test
	void downloadFileById_shouldUseAFallbackNameWhenTheAssetHasNoUsableFilename() {
		when(fileLogic.downloadImageFileById("file-1", null))
			.thenReturn(imageRdo(new byte[] {1}, "image/png", "original", ""));

		var response = fileResource.downloadFileById("file-1", null, null);

		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("download"));
	}

	@Test
	void getFileById_shouldReportOriginalEtagWhenVariantFallsBackToOriginal() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {1}, "image/png", "original"));

		var response = fileResource.getFileById("file-1", "home-thumb", null, null, null);

		assertEquals("\"file-1-original\"", response.getHeaders().getETag());
	}

	@Test
	void getFileById_shouldReturnNotModifiedWithoutReadingFileWhenEtagMatches() {
		var response = fileResource.getFileById("file-1", "home-thumb", null, null, "\"file-1-home-thumb\"");

		assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
		assertNull(response.getBody());
		assertEquals("\"file-1-home-thumb\"", response.getHeaders().getETag());
		verify(fileLogic, never()).getImageFileById(anyString(), any());
	}

	@Test
	void getFileById_shouldAcceptWeakAndListedEtagCandidates() {
		var response = fileResource.getFileById("file-1", null, null, null,
			"\"other\", W/\"file-1-original\"");

		assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
		verify(fileLogic, never()).getImageFileById(anyString(), any());
	}

	@Test
	void getFileById_shouldServeContentWhenEtagBelongsToADifferentVariant() {
		when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/jpeg", "home-thumb"));

		var response = fileResource.getFileById("file-1", "home-thumb", null, null, "\"file-1-original\"");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertArrayEquals(new byte[] {9}, response.getBody());
	}

	@Test
	void getFile_shouldReturnNotModifiedWhenEtagMatches() {
		var response = fileResource.getFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png",
			"\"logo/72d768d4-2b05-48f9-bee8-fee3b52e909f.png-original\"");

		assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
		verify(fileLogic, never()).getImageFile(anyString(), anyString());
	}

	@Test
	void getFileById_shouldRedirectWhenTheLogicReturnsASignedUrl() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
		assertEquals("https://r2.example.com/signed", response.getHeaders().getLocation().toString());
		assertNull(response.getBody());
	}

	@Test
	void getFileById_shouldNotCacheOrTagARedirectBecauseTheSignedUrlExpires() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertNull(response.getHeaders().getETag());
		assertEquals("no-store", response.getHeaders().getCacheControl());
	}

	@Test
	void downloadFileById_shouldRedirectAndLeaveTheDispositionToTheSignedUrl() {
		when(fileLogic.downloadImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.downloadFileById("file-1", null, null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
		assertEquals("https://r2.example.com/signed", response.getHeaders().getLocation().toString());
		assertNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
	}

	@Test
	void getFile_shouldRedirectWhenTheLogicReturnsASignedUrl() {
		when(fileLogic.getImageFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.thenReturn(redirectRdo("original"));

		var response = fileResource.getFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png", null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
	}

	@Test
	void downloadFileById_shouldStillStreamVariantBytesWithItsOwnDisposition() {
		when(fileLogic.downloadImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/webp", "home-thumb", "cover_home-thumb.webp"));

		var response = fileResource.downloadFileById("file-1", "home-thumb", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
			.contains("cover_home-thumb.webp"));
	}

	private ImageFileRdo redirectRdo(String variant) {
		return ImageFileRdo.builder()
			.redirectUrl("https://r2.example.com/signed")
			.variant(variant)
			.downloadFilename("cover.png")
			.build();
	}

	private ImageFileRdo imageRdo(byte[] image, String mimeType, String variant) {
		return imageRdo(image, mimeType, variant, "cover.png");
	}

	private ImageFileRdo imageRdo(byte[] image, String mimeType, String variant, String downloadFilename) {
		return ImageFileRdo.builder()
			.image(image)
			.mimeType(mimeType)
			.variant(variant)
			.downloadFilename(downloadFilename)
			.build();
	}
}
