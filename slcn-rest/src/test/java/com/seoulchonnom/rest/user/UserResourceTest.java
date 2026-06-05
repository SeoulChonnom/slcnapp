package com.seoulchonnom.rest.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.auth.flow.UserFlow;
import com.seoulchonnom.auth.flow.vo.UserSessionVo;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

class UserResourceTest {
	@Test
	void loginUser_shouldSetRefreshTokenAndSessionIdCookies() {
		UserFlow userFlow = mock(UserFlow.class);
		UserResource userResource = new UserResource(userFlow);
		configureCookieProperties(userResource);
		MockHttpServletResponse response = new MockHttpServletResponse();
		UserSessionVo userSessionVo = UserSessionVo.builder()
			.sessionId("session-1")
			.tokenRdo(TokenRdo.builder().refreshToken("refresh-token").build())
			.userRdo(UserRdo.builder().accessToken("access-token").build())
			.build();
		when(userFlow.login(any(UserLoginCdo.class))).thenReturn(userSessionVo);

		ResponseEntity<UserRdo> entity = userResource.loginUser(response, UserLoginCdo.builder().build());

		assertEquals(HttpStatus.OK, entity.getStatusCode());
		assertEquals("access-token", entity.getBody().getAccessToken());
		List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		assertEquals(2, setCookies.size());
		assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("refreshToken=refresh-token")));
		assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("sessionId=session-1")));
	}

	@Test
	void reissueToken_shouldRefreshBothCookies() {
		UserFlow userFlow = mock(UserFlow.class);
		UserResource userResource = new UserResource(userFlow);
		configureCookieProperties(userResource);
		MockHttpServletResponse response = new MockHttpServletResponse();
		UserSessionVo userSessionVo = UserSessionVo.builder()
			.sessionId("session-1")
			.tokenRdo(TokenRdo.builder().refreshToken("rotated-refresh").build())
			.userRdo(UserRdo.builder().accessToken("access-token").build())
			.build();
		when(userFlow.reissue("refresh-token", "session-1")).thenReturn(userSessionVo);

		ResponseEntity<UserRdo> entity = userResource.reissueToken("refresh-token", "session-1", response);

		assertEquals(HttpStatus.OK, entity.getStatusCode());
		verify(userFlow).reissue("refresh-token", "session-1");
		List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		assertEquals(2, setCookies.size());
		assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("refreshToken=rotated-refresh")));
		assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("sessionId=session-1")));
	}

	@Test
	void logoutUser_shouldDeleteCurrentSessionAndExpireCookies() {
		UserFlow userFlow = mock(UserFlow.class);
		UserResource userResource = new UserResource(userFlow);
		configureCookieProperties(userResource);
		MockHttpServletResponse response = new MockHttpServletResponse();

		ResponseEntity<Void> entity = userResource.logoutUser("session-1", response);

		assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
		verify(userFlow).logout("session-1");
		List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		assertEquals(2, setCookies.size());
		assertTrue(setCookies.stream().allMatch(cookie -> cookie.contains("Max-Age=0")));
	}

	private void configureCookieProperties(UserResource userResource) {
		ReflectionTestUtils.setField(userResource, "refreshCookieMaxAge", 1209600L);
		ReflectionTestUtils.setField(userResource, "refreshCookieSecure", false);
		ReflectionTestUtils.setField(userResource, "refreshCookieSameSite", "Lax");
	}
}
