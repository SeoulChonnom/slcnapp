package com.seoulchonnom.auth.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class CommonAccessDeniedHandlerTest {
	@Test
	void writesForbiddenResponseBody() throws Exception {
		CommonAccessDeniedHandler handler = new CommonAccessDeniedHandler(new ObjectMapper());
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(request, response, new AccessDeniedException("denied"));

		assertEquals(ErrorCode.ACCESS_ROLE_DENIED.getHttpStatus().value(), response.getStatus());
		assertTrue(response.getContentAsString().contains(ErrorCode.ACCESS_ROLE_DENIED.getMessage()));
	}
}
