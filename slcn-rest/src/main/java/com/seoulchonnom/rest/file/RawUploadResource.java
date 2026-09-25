package com.seoulchonnom.rest.file;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.file.logic.RawUploadLogic;
import com.seoulchonnom.spec.file.facade.RawUploadFacade;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadCompleteCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("assets/raw-uploads")
@RequiredArgsConstructor
public class RawUploadResource implements RawUploadFacade {
	private final RawUploadLogic rawUploadLogic;

	@Override
	@PostMapping
	public ResponseEntity<RawUploadSessionRdo> createSession(@RequestBody RawUploadSessionCdo cdo) {
		return new ResponseEntity<>(rawUploadLogic.createSession(cdo), HttpStatus.CREATED);
	}

	@Override
	@PostMapping("/{fileId}/complete")
	public ResponseEntity<FileAssetRdo> complete(@PathVariable("fileId") String fileId,
		@RequestBody RawUploadCompleteCdo cdo) {
		return ResponseEntity.ok(FileAssetRdo.from(rawUploadLogic.complete(fileId, cdo)));
	}

	@Override
	@DeleteMapping("/{fileId}")
	public ResponseEntity<Void> delete(@PathVariable("fileId") String fileId) {
		rawUploadLogic.delete(fileId);
		return ResponseEntity.noContent().build();
	}
}
