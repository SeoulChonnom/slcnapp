package com.seoulchonnom.slcnapp.depot.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Depot API", description = "파일 관련 API")
public interface DepotControllerDocs {

    @Operation(summary = "파일 업로드 API", description = "파일을 업로드합니다.")
    @Parameters({
            @Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
    @ApiResponse(responseCode = "200", description = "파일 업로드 성공")
    ResponseEntity<BaseResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path);

    @Operation(summary = "파일 조회 API", description = "파일 경로를 통해 파일을 조회합니다.")
    @Parameters({
            @Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
    @ApiResponse(responseCode = "200", description = "파일 조회 성공")
    ResponseEntity<byte[]> getFile(@RequestParam(value = "path") String path);
}