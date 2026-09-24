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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

	@Test
	void calendarFeed_withValidTokenWithoutJwt_shouldReachResource() throws Exception {
		mockMvc.perform(get("/schedule/feeds/valid-token/calendar.ics"))
			.andExpect(status().isOk());
	}

	@Test
	void calendarFeed_withApiContextPath_shouldReachResource() throws Exception {
		mockMvc.perform(get("/api/schedule/feeds/valid-token/calendar.ics").contextPath("/api"))
			.andExpect(status().isOk());
	}

	@Test
	void calendarFeed_withResourceCachePolicy_shouldKeepPrivateNoCacheUnderSecurityChain() throws Exception {
		mockMvc.perform(get("/schedule/feeds/valid-token/calendar.ics"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-cache, private"));
	}

	@Test
	void feedManagementCreate_withoutJwt_shouldReturnUnauthorized() throws Exception {
		mockMvc.perform(post("/schedule/feeds"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void feedManagementList_withoutJwt_shouldReturnUnauthorized() throws Exception {
		mockMvc.perform(get("/schedule/feeds"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void feedManagementDelete_withoutJwt_shouldReturnUnauthorized() throws Exception {
		mockMvc.perform(delete("/schedule/feeds/feed-id"))
			.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "CLIENT"})
	void feedManagement_withNonAdminJwt_shouldReturnForbidden(String authority) throws Exception {
		String token = authority.toLowerCase() + "-token";
		givenValidAccessToken(token, authority);

		mockMvc.perform(post("/schedule/feeds")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, token))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/schedule/feeds")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, token))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/schedule/feeds/feed-id")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, token))
			.andExpect(status().isForbidden());
	}

	@Test
	void feedManagement_withAdminJwt_shouldReachResource() throws Exception {
		givenValidAccessToken("admin-token", "ADMIN");

		mockMvc.perform(post("/schedule/feeds")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, "admin-token"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/schedule/feeds")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, "admin-token"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/schedule/feeds/feed-id")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, "admin-token"))
			.andExpect(status().isNoContent());
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "CLIENT"})
	void invalidCalendarFeedToken_withValidJwt_shouldReturnNotFound(String authority) throws Exception {
		String token = authority.toLowerCase() + "-token";
		givenValidAccessToken(token, authority);

		mockMvc.perform(get("/schedule/feeds/invalid-token/calendar.ics")
				.header(AuthConstant.ACCESS_TOKEN_HEADER_NAME, token))
			.andExpect(status().isNotFound());
	}

	@Test
	void calendarFeed_wrongMethodOrNearMiss_withoutJwt_shouldRemainProtected() throws Exception {
		mockMvc.perform(post("/schedule/feeds/valid-token/calendar.ics"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/schedule/feeds/valid-token/calendar.ics"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/schedule/feeds/valid-token/calendar.ics"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/schedule/feeds/valid-token/not-calendar.ics"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/schedule/feeds/valid-token/calendar.ics/extra"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void unsupportedFeedManagementPath_withoutJwt_shouldRemainProtected() throws Exception {
		mockMvc.perform(get("/schedule/feeds/feed-id/extra"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void existingScheduleApi_withoutJwt_shouldRemainProtected() throws Exception {
		mockMvc.perform(get("/schedule"))
			.andExpect(status().isUnauthorized());
	}

	private void givenLiveSession() {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenReturn(Optional.of(new RefreshSession(
			SESSION_ID, USER_ID, "hash", System.currentTimeMillis(), System.currentTimeMillis() + 60_000)));
	}

	private void givenValidAccessToken() {
		givenValidAccessToken("access-token", "USER");
	}

	private void givenValidAccessToken(String token, String authority) {
		Claims claims = mock(Claims.class);
		Authentication authentication = new UsernamePasswordAuthenticationToken(
			USER_ID, "", List.of(new SimpleGrantedAuthority(authority)));
		when(jwtTokenProvider.resolveToken(any())).thenReturn(token);
		when(jwtTokenProvider.validateAccessToken(token)).thenReturn(TokenValidationResult.valid(claims));
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

		@Bean
		StubScheduleFeedController stubScheduleFeedController() {
			return new StubScheduleFeedController();
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

	@RestController
	static class StubScheduleFeedController {
		@PostMapping("/schedule/feeds")
		ResponseEntity<String> createFeed() {
			return ResponseEntity.ok("created");
		}

		@GetMapping("/schedule/feeds")
		ResponseEntity<String> getFeeds() {
			return ResponseEntity.ok("feeds");
		}

		@DeleteMapping("/schedule/feeds/{feedId}")
		ResponseEntity<Void> deleteFeed(@PathVariable("feedId") String feedId) {
			return ResponseEntity.noContent().build();
		}

		@GetMapping(value = "/schedule/feeds/{feedToken}/calendar.ics", produces = "text/calendar; charset=UTF-8")
		ResponseEntity<String> getCalendar(@PathVariable("feedToken") String feedToken) {
			if (!"valid-token".equals(feedToken)) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
			}
			return ResponseEntity.ok()
				.cacheControl(CacheControl.noCache().cachePrivate())
				.body("calendar");
		}

		@GetMapping("/schedule")
		ResponseEntity<String> getSchedule() {
			return ResponseEntity.ok("schedule");
		}
	}
}
