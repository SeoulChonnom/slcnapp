package com.seoulchonnom.rest.file;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;
import com.seoulchonnom.spec.file.facade.FileFacade;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("assets")
@RequiredArgsConstructor
public class FileResource implements FileFacade {
	private final FileLogic fileLogic;

	@Override
	@PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FileAssetRdo> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("type") String type) {
		return new ResponseEntity<>(FileAssetRdo.from(fileLogic.uploadFile(file, type)), HttpStatus.OK);
	}

	@Override
	@PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<List<FileAssetRdo>> uploadFiles(@RequestParam("files") List<MultipartFile> files,
		@RequestParam("type") String type) {
		return new ResponseEntity<>(
			fileLogic.uploadFiles(files, type).stream().map(FileAssetRdo::from).toList(),
			HttpStatus.OK);
	}

	@Override
	@GetMapping("/files/{fileId}")
	public ResponseEntity<byte[]> getFileById(@PathVariable("fileId") String fileId,
		@RequestParam(value = "variant", required = false) String variant,
		@RequestParam(value = "width", required = false) Integer width,
		@RequestParam(value = "format", required = false) String format,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		ImageVariant requestedVariant = resolveVariant(variant, width);
		String requestedTag = requestedVariant == null ? ORIGINAL_VARIANT_TAG : requestedVariant.getValue();

		// 파일을 읽기 전에 먼저 비교한다. 304면 디스크를 건드릴 이유가 없다.
		if (isNotModified(ifNoneMatch, etagOf(fileId, requestedTag))) {
			return notModified(etagOf(fileId, requestedTag));
		}

		ImageFileRdo imageFileRdo = fileLogic.getImageFileById(fileId, requestedVariant);
		return toImageResponse(imageFileRdo, etagOf(fileId, imageFileRdo.getVariant()));
	}

	@Override
	@GetMapping("/files/{fileId}/download")
	public ResponseEntity<byte[]> downloadFileById(@PathVariable("fileId") String fileId,
		@RequestParam(value = "variant", required = false) String variant,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		ImageVariant requestedVariant = resolveDownloadVariant(variant);
		String requestedTag = requestedVariant == null ? ORIGINAL_VARIANT_TAG : requestedVariant.getValue();

		String etag = downloadEtagOf(fileId, requestedTag);
		if (isNotModified(ifNoneMatch, etag)) {
			return notModified(etag);
		}

		ImageFileRdo imageFileRdo = fileLogic.getImageFileById(fileId, requestedVariant);
		return toImageResponse(imageFileRdo, downloadEtagOf(fileId, imageFileRdo.getVariant()),
			attachmentDisposition(imageFileRdo.getDownloadFilename()));
	}

	@Override
	@GetMapping("/file")
	public ResponseEntity<byte[]> getFile(@RequestParam("type") String type, @RequestParam("filename") String filename,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		String etag = etagOf(type + "/" + filename, ORIGINAL_VARIANT_TAG);
		if (isNotModified(ifNoneMatch, etag)) {
			return notModified(etag);
		}

		return toImageResponse(fileLogic.getImageFile(type, filename), etag);
	}

	/**
	 * variant가 우선하고, 없으면 width로 고른다. 알 수 없는 variant는 오류가 아니라 기본 축소본으로 처리한다.
	 * 4xx를 주면 프론트가 원본으로 재시도하는 왕복이 생기고, 원본으로 폴백하면 오타 하나에 수 MB가 나간다.
	 * 원본이 필요하면 파라미터를 비우거나 variant=original을 쓴다.
	 */
	private ImageVariant resolveVariant(String variant, Integer width) {
		if (StringUtils.hasText(variant)) {
			if (isOriginalRequest(variant)) {
				return null;
			}
			return ImageVariant.from(variant).orElseGet(ImageVariant::defaultVariant);
		}

		return ImageVariant.coveringWidth(width).orElse(null);
	}

	/**
	 * 저장은 조회와 기본값이 다르다. 사용자가 파일로 남기려는 것은 원본이므로,
	 * variant를 명시하지 않았거나 알 수 없는 값이면 축소본으로 바꿔치지 않고 원본을 내려준다.
	 */
	private ImageVariant resolveDownloadVariant(String variant) {
		if (!StringUtils.hasText(variant) || isOriginalRequest(variant)) {
			return null;
		}

		return ImageVariant.from(variant).orElse(null);
	}

	private boolean isOriginalRequest(String variant) {
		return ORIGINAL_VARIANT_TAG.equalsIgnoreCase(variant.trim());
	}

	/**
	 * 파일 ID와 저장 파일명이 불변이라 내용 해시 없이 식별자만으로 강한 ETag를 만들 수 있다.
	 */
	private String etagOf(String identity, String variantTag) {
		return "\"" + identity + "-" + variantTag + "\"";
	}

	private boolean isNotModified(String ifNoneMatch, String etag) {
		if (!StringUtils.hasText(ifNoneMatch)) {
			return false;
		}

		for (String candidate : ifNoneMatch.split(",")) {
			String trimmed = candidate.trim();
			if ("*".equals(trimmed)) {
				return true;
			}
			if (trimmed.startsWith("W/")) {
				trimmed = trimmed.substring(2);
			}
			if (etag.equals(trimmed)) {
				return true;
			}
		}

		return false;
	}

	private ResponseEntity<byte[]> notModified(String etag) {
		return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
			.eTag(etag)
			.cacheControl(imageCacheControl())
			.build();
	}

	private ResponseEntity<byte[]> toImageResponse(ImageFileRdo imageFileRdo, String etag) {
		return toImageResponse(imageFileRdo, etag, null);
	}

	private ResponseEntity<byte[]> toImageResponse(ImageFileRdo imageFileRdo, String etag,
		ContentDisposition contentDisposition) {
		MediaType mediaType = imageFileRdo.getMimeType() == null
			? MediaType.APPLICATION_OCTET_STREAM
			: MediaType.parseMediaType(imageFileRdo.getMimeType());

		ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
			.contentType(mediaType)
			.contentLength(imageFileRdo.getImage().length)
			.eTag(etag)
			.cacheControl(imageCacheControl());
		if (contentDisposition != null) {
			builder.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
		}

		return builder.body(imageFileRdo.getImage());
	}

	/**
	 * 한글 파일명이 들어올 수 있으므로 RFC 5987 인코딩을 함께 내보낸다.
	 */
	private ContentDisposition attachmentDisposition(String filename) {
		String safeFilename = StringUtils.hasText(filename) ? filename : "download";
		return ContentDisposition.attachment()
			.filename(safeFilename, StandardCharsets.UTF_8)
			.build();
	}

	/**
	 * 같은 바이트라도 첨부 응답은 헤더가 다르므로 조회 응답과 ETag를 섞지 않는다.
	 */
	private String downloadEtagOf(String fileId, String variantTag) {
		return etagOf(fileId, variantTag + "-download");
	}

	/**
	 * 응답이 로그인 사용자에게만 허용되므로 공유 캐시에 남으면 안 된다. private으로 고정한다.
	 */
	private CacheControl imageCacheControl() {
		return CacheControl.maxAge(IMAGE_CACHE_MAX_AGE_SECONDS, TimeUnit.SECONDS).cachePrivate();
	}
}
