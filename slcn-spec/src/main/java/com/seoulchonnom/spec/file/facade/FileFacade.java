package com.seoulchonnom.spec.file.facade;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface FileFacade {
	@Operation(summary = "파일 업로드 API", description = "파일을 업로드합니다.")
	@ApiResponse(responseCode = "200", description = "파일 업로드 성공")
	ResponseEntity<String> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("path") String path);

	@Operation(summary = "파일 조회 API", description = "파일 경로를 통해 파일을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "파일 조회 성공")
	ResponseEntity<byte[]> getFile(@RequestParam(value = "path") String path);

	@Tag(name = "Depot API", description = "파일 관련 API")
	interface DepotControllerDocs extends FileFacade {
	}
}
