package com.seoulchonnom.spec.client.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "클라이언트 인증 API", description = "서비스 간 토큰 발급")
public interface ClientFacade {
	@Operation(summary = "클라이언트 토큰 발급", description = "client_id와 client_secret으로 access token을 발급합니다.")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
	ResponseEntity<ClientTokenRdo> issueClientToken(ClientTokenCdo clientTokenCdo);
}
