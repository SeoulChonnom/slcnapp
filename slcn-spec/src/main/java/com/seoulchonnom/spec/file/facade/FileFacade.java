package com.seoulchonnom.spec.file.facade;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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

	@Operation(summary = "파일 ID 조회 API",
		description = "FileAsset ID를 통해 파일을 조회합니다. "
			+ "variant(home-feature, home-thumb) 또는 width를 지정하면 축소본을 응답합니다. "
			+ "파라미터를 지정하지 않거나 variant=original이면 원본을 응답하고, "
			+ "알 수 없는 variant는 기본 축소본(home-feature)으로 응답합니다. "
			+ "축소본이 아직 생성되지 않은 자산은 원본으로 폴백합니다. "
			+ "format 파라미터는 호환을 위해 받기만 하고 사용하지 않습니다.")
	@ApiResponse(responseCode = "200", description = "파일 조회 성공")
	@ApiResponse(responseCode = "304", description = "ETag 일치, 본문 없음")
	ResponseEntity<byte[]> getFileById(
		@PathVariable("fileId") String fileId,
		@RequestParam(value = "variant", required = false) String variant,
		@RequestParam(value = "width", required = false) Integer width,
		@RequestParam(value = "format", required = false) String format,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch);

	@Operation(summary = "파일 다운로드 API",
		description = "FileAsset ID의 파일을 첨부 파일로 내려받습니다. 기본은 원본이며, "
			+ "variant를 지정하면 해당 축소본을 내려받습니다. 알 수 없는 variant는 원본으로 처리합니다. "
			+ "Content-Disposition에 업로드 당시의 파일명이 담깁니다.")
	@ApiResponse(responseCode = "200", description = "파일 다운로드 성공")
	@ApiResponse(responseCode = "304", description = "ETag 일치, 본문 없음")
	ResponseEntity<byte[]> downloadFileById(
		@PathVariable("fileId") String fileId,
		@RequestParam(value = "variant", required = false) String variant,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch);

	@Operation(summary = "파일 조회 API", description = "파일 경로를 통해 파일을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "파일 조회 성공")
	@ApiResponse(responseCode = "304", description = "ETag 일치, 본문 없음")
	ResponseEntity<byte[]> getFile(
		@RequestParam(value = "type") String type,
		@RequestParam(value = "filename") String filename,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch);

	@Tag(name = "Depot API", description = "파일 관련 API")
	interface DepotControllerDocs extends FileFacade {
	}
}
