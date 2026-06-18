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
import com.seoulchonnom.spec.file.facade.sdo.FileRefSdo;
import com.seoulchonnom.spec.file.facade.sdo.ImageFileRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class
FileResource implements FileFacade {
	private final FileLogic fileLogic;

	@Override
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FileRefSdo> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("type") String type) {
		return new ResponseEntity<>(FileRefSdo.from(fileLogic.uploadFile(file, type)), HttpStatus.OK);
	}

	@Override
	@GetMapping
	public ResponseEntity<byte[]> getFile(@RequestParam("type") String type, @RequestParam("filename") String filename) {
		ImageFileRdo imageFileRdo = fileLogic.getImageFile(type, filename);
		MediaType mediaType = imageFileRdo.getMimeType() == null
			? MediaType.APPLICATION_OCTET_STREAM
			: MediaType.parseMediaType(imageFileRdo.getMimeType());

		return ResponseEntity.ok()
			.contentType(mediaType)
			.contentLength(imageFileRdo.getImage().length)
			.body(imageFileRdo.getImage());
	}
}
