package com.seoulchonnom.rest.common.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.spec.common.response.ErrorResponse;

class CommonExceptionHandlerTest {
	@Test
	void mapsBusinessExceptionUsingErrorCodeStatus() {
		CommonExceptionHandler handler = new CommonExceptionHandler();

		ResponseEntity<ErrorResponse> response = handler.businessException(new InvalidUserException());

		assertEquals(400, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals("로그인 정보가 잘못되었습니다.", response.getBody().getMessage());
	}
}
