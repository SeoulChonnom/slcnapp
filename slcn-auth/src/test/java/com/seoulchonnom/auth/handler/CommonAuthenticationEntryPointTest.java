package com.seoulchonnom.auth.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class CommonAuthenticationEntryPointTest {
	@Test
	void writesUnauthorizedResponseBody() throws Exception {
		CommonAuthenticationEntryPoint entryPoint = new CommonAuthenticationEntryPoint(new ObjectMapper());
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		entryPoint.commence(request, response, new BadCredentialsException("invalid"));

		assertEquals(ErrorCode.UNAUTHORIZED.getHttpStatus().value(), response.getStatus());
		assertTrue(response.getContentAsString().contains(ErrorCode.UNAUTHORIZED.getMessage()));
	}
}
