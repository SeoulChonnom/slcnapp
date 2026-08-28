package com.seoulchonnom.rest.file;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
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
	 * variant가 우선하고, 없으면 width로 고른다. 알 수 없는 값은 오류가 아니라 원본 요청으로 취급한다.
	 * 프론트가 실패 후 원본으로 재시도하는 왕복을 없애기 위한 선택이다.
	 */
	private ImageVariant resolveVariant(String variant, Integer width) {
		if (StringUtils.hasText(variant)) {
			return ImageVariant.from(variant).orElse(null);
		}

		return ImageVariant.coveringWidth(width).orElse(null);
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
		MediaType mediaType = imageFileRdo.getMimeType() == null
			? MediaType.APPLICATION_OCTET_STREAM
			: MediaType.parseMediaType(imageFileRdo.getMimeType());

		return ResponseEntity.ok()
			.contentType(mediaType)
			.contentLength(imageFileRdo.getImage().length)
			.eTag(etag)
			.cacheControl(imageCacheControl())
			.body(imageFileRdo.getImage());
	}

	/**
	 * 응답이 로그인 사용자에게만 허용되므로 공유 캐시에 남으면 안 된다. private으로 고정한다.
	 */
	private CacheControl imageCacheControl() {
		return CacheControl.maxAge(IMAGE_CACHE_MAX_AGE_SECONDS, TimeUnit.SECONDS).cachePrivate();
	}
}
