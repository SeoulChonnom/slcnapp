package com.seoulchonnom.rest.file;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
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
	public ResponseEntity<byte[]> getFileById(@PathVariable("fileId") String fileId) {
		return toImageResponse(fileLogic.getImageFileById(fileId));
	}

	@Override
	@GetMapping("/file")
	public ResponseEntity<byte[]> getFile(@RequestParam("type") String type, @RequestParam("filename") String filename) {
		return toImageResponse(fileLogic.getImageFile(type, filename));
	}

	private ResponseEntity<byte[]> toImageResponse(ImageFileRdo imageFileRdo) {
		MediaType mediaType = imageFileRdo.getMimeType() == null
			? MediaType.APPLICATION_OCTET_STREAM
			: MediaType.parseMediaType(imageFileRdo.getMimeType());

		return ResponseEntity.ok()
			.contentType(mediaType)
			.contentLength(imageFileRdo.getImage().length)
			.body(imageFileRdo.getImage());
	}
}
