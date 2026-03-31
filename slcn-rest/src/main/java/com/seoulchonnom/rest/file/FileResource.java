package com.seoulchonnom.rest.file;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.logic.FileLogic;
import com.seoulchonnom.spec.file.facade.FileFacade;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class
FileResource implements FileFacade {
	private final FileLogic fileLogic;

	@Override
	@PostMapping
	public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("path") String path) {
		return new ResponseEntity<>(fileLogic.uploadFile(file, path), HttpStatus.OK);
	}

	@Override
	@GetMapping
	public ResponseEntity<byte[]> getFile(@RequestParam("path") String path) {
		ImageFileRdo imageFileRdo = fileLogic.getImageFile(path);
		MediaType mediaType = imageFileRdo.getMimeType() == null
			? MediaType.APPLICATION_OCTET_STREAM
			: MediaType.parseMediaType(imageFileRdo.getMimeType());

		return ResponseEntity.ok()
			.contentType(mediaType)
			.contentLength(imageFileRdo.getImage().length)
			.body(imageFileRdo.getImage());
	}
}
