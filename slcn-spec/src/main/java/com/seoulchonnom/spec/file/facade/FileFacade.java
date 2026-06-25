package com.seoulchonnom.spec.file.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface FileFacade {
	@Operation(summary = "파일 업로드 API", description = "파일을 업로드합니다.")
	@ApiResponse(responseCode = "200", description = "파일 업로드 성공")
	ResponseEntity<FileAssetRdo> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("type") String type);

	@Operation(summary = "다중 파일 업로드 API", description = "여러 이미지 파일을 업로드하고 FileAsset ID 목록을 반환합니다.")
	@ApiResponse(responseCode = "200", description = "파일 업로드 성공")
	ResponseEntity<List<FileAssetRdo>> uploadFiles(
		@RequestParam("files") List<MultipartFile> files,
		@RequestParam("type") String type);

	@Operation(summary = "파일 ID 조회 API", description = "FileAsset ID를 통해 파일을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "파일 조회 성공")
	ResponseEntity<byte[]> getFileById(@PathVariable("fileId") String fileId);

	@Operation(summary = "파일 조회 API", description = "파일 경로를 통해 파일을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "파일 조회 성공")
	ResponseEntity<byte[]> getFile(
		@RequestParam(value = "type") String type,
		@RequestParam(value = "filename") String filename);

	@Tag(name = "Depot API", description = "파일 관련 API")
	interface DepotControllerDocs extends FileFacade {
	}
}
