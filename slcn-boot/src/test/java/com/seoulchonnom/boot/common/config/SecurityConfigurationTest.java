package com.seoulchonnom.boot.common.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.filter.ImageSessionAuthenticationFilter;
import com.seoulchonnom.auth.filter.JwtAuthenticationFilter;
import com.seoulchonnom.auth.handler.CommonAccessDeniedHandler;
import com.seoulchonnom.auth.handler.CommonAuthenticationEntryPoint;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.store.projection.RefreshSession;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;

import io.jsonwebtoken.Claims;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;

/**
 * 이미지 조회 체인과 기본 체인이 나뉘어 동작하는지 확인한다.
 * 특히 sessionId 쿠키가 이미지 조회 밖으로 새어나가지 않는지, 그리고 다른 경로의
 * 미인증 요청이 403이 아니라 401로 끝나는지를 검증한다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfiguration.class, SecurityConfigurationTest.TestConfig.class})
class SecurityConfigurationTest {
	private static final String FILE_ID = "52cce541-8752-4c00-9b91-452272a5d98f";
	private static final String IMAGE_PATH = "/assets/files/" + FILE_ID;
	private static final String SESSION_ID = "session-1";
	private static final String USER_ID = "USER-0001";

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private Filter springSecurityFilterChain;

	@Autowired
	private RefreshSessionStore refreshSessionStore;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		reset(refreshSessionStore, jwtTokenProvider);
		when(jwtTokenProvider.resolveToken(any())).thenReturn(null);
		when(jwtTokenProvider.validateAccessToken(any())).thenReturn(TokenValidationResult.invalid(null, null));
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.addFilters(springSecurityFilterChain)
			.build();
	}

	@Test
	void imageLookup_withSessionCookieOnly_shouldReturnOk() throws Exception {
		givenLiveSession();

		mockMvc.perform(get(IMAGE_PATH)
				.cookie(sessionCookie())
				.header("Sec-Fetch-Site", "same-site")
				.header("Sec-Fetch-Dest", "image"))
			.andExpect(status().isOk());
	}

	@Test
	void pathBasedImageLookup_withSessionCookieOnly_shouldReturnOk() throws Exception {
		givenLiveSession();

		mockMvc.perform(get("/assets/file")
				.cookie(sessionCookie())
				.header("Sec-Fetch-Site", "same-origin")
				.header("Sec-Fetch-Dest", "image"))
			.andExpect(status().isOk());
	}

	@Test
	void imageLookup_withoutCredentials_shouldReturnUnauthorized() throws Exception {
		mockMvc.perform(get(IMAGE_PATH))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void imageLookup_fromCrossSitePage_shouldReturnUnauthorized() throws Exception {
		givenLiveSession();

		mockMvc.perform(get(IMAGE_PATH)
				.cookie(sessionCookie())
				.header("Sec-Fetch-Site", "cross-site")
				.header("Sec-Fetch-Dest", "image"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void imageLookup_withAccessToken_shouldStillReturnOk() throws Exception {
		givenValidAccessToken();

		mockMvc.perform(get(IMAGE_PATH).header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, "access-token"))
			.andExpect(status().isOk());
		verifyNoInteractions(refreshSessionStore);
	}

	@Test
	void download_withSessionCookieOnly_shouldReturnUnauthorized() throws Exception {
		givenLiveSession();

		mockMvc.perform(get(IMAGE_PATH + "/download")
				.cookie(sessionCookie())
				.header("Sec-Fetch-Site", "same-site")
				.header("Sec-Fetch-Dest", "image"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(refreshSessionStore);
	}

	/**
	 * 쿠키 인증이 이미지 조회 밖으로 새면 액세스 토큰이 만료된 요청이 403이 되어
	 * 프론트가 재발급을 시도하지 못한다. 반드시 401이어야 한다.
	 */
	@Test
	void otherResource_withSessionCookieOnly_shouldReturnUnauthorizedNotForbidden() throws Exception {
		givenLiveSession();

		mockMvc.perform(get("/travels")
				.cookie(sessionCookie())
				.header("Sec-Fetch-Site", "same-site")
				.header("Sec-Fetch-Dest", "empty"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(refreshSessionStore);
	}

	private void givenLiveSession() {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenReturn(Optional.of(new RefreshSession(
			SESSION_ID, USER_ID, "hash", System.currentTimeMillis(), System.currentTimeMillis() + 60_000)));
	}

	private void givenValidAccessToken() {
		Claims claims = mock(Claims.class);
		Authentication authentication = new UsernamePasswordAuthenticationToken(
			USER_ID, "", List.of(new SimpleGrantedAuthority("USER")));
		when(jwtTokenProvider.resolveToken(any())).thenReturn("access-token");
		when(jwtTokenProvider.validateAccessToken("access-token")).thenReturn(TokenValidationResult.valid(claims));
		when(jwtTokenProvider.getAuthentication(claims)).thenReturn(authentication);
	}

	private Cookie sessionCookie() {
		return new Cookie(AuthConstant.SESSION_ID_COOKIE_NAME, SESSION_ID);
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		JwtTokenProvider jwtTokenProvider() {
			return mock(JwtTokenProvider.class);
		}

		@Bean
		RefreshSessionStore refreshSessionStore() {
			return mock(RefreshSessionStore.class);
		}

		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
			return new JwtAuthenticationFilter(jwtTokenProvider);
		}

		@Bean
		ImageSessionAuthenticationFilter imageSessionAuthenticationFilter(RefreshSessionStore refreshSessionStore) {
			return new ImageSessionAuthenticationFilter(refreshSessionStore);
		}

		@Bean
		CommonAuthenticationEntryPoint commonAuthenticationEntryPoint(ObjectMapper objectMapper) {
			return new CommonAuthenticationEntryPoint(objectMapper);
		}

		@Bean
		CommonAccessDeniedHandler commonAccessDeniedHandler(ObjectMapper objectMapper) {
			return new CommonAccessDeniedHandler(objectMapper);
		}

		@Bean
		StubAssetController stubAssetController() {
			return new StubAssetController();
		}
	}

	@RestController
	static class StubAssetController {
		@GetMapping("/assets/files/{fileId}")
		ResponseEntity<String> image(@PathVariable("fileId") String fileId) {
			return ResponseEntity.ok(fileId);
		}

		@GetMapping("/assets/files/{fileId}/download")
		ResponseEntity<String> download(@PathVariable("fileId") String fileId) {
			return ResponseEntity.ok(fileId);
		}

		@GetMapping("/assets/file")
		ResponseEntity<String> pathBasedImage() {
			return ResponseEntity.ok("image");
		}

		@GetMapping("/travels")
		ResponseEntity<String> travels() {
			return ResponseEntity.ok("travels");
		}
	}
}
