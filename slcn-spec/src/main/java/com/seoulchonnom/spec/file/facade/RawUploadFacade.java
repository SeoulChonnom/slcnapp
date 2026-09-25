package com.seoulchonnom.spec.file.facade;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadCompleteCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionCdo;
import com.seoulchonnom.spec.file.facade.sdo.RawUploadSessionRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface RawUploadFacade {
	@Operation(summary = "RAW 업로드 세션 생성 API",
		description = "여행 사진의 RAW(RAF) 첨부를 저장소에 직접 올릴 파트별 서명 URL을 발급합니다.")
	@ApiResponse(responseCode = "201", description = "세션 생성 성공")
	@ApiResponse(responseCode = "501", description = "서명 URL을 지원하지 않는 저장소 설정")
	ResponseEntity<RawUploadSessionRdo> createSession(@RequestBody RawUploadSessionCdo cdo);

	@Operation(summary = "RAW 업로드 완료 API", description = "파트 ETag로 업로드를 완료하고 크기와 RAF 매직을 검증합니다.")
	@ApiResponse(responseCode = "200", description = "완료 성공. 이미 완료된 업로드도 같은 응답")
	ResponseEntity<FileAssetRdo> complete(@PathVariable("fileId") String fileId, @RequestBody RawUploadCompleteCdo cdo);

	@Operation(summary = "RAW 업로드 취소·삭제 API",
		description = "업로드 중이면 세션을 중단하고, 여행에 연결되지 않은 완료 RAW는 삭제합니다.")
	@ApiResponse(responseCode = "204", description = "삭제 성공")
	@ApiResponse(responseCode = "409", description = "여행에 연결된 RAW")
	ResponseEntity<Void> delete(@PathVariable("fileId") String fileId);
}
