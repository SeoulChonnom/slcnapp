package com.seoulchonnom.auth.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.store.projection.RefreshSession;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

class ImageSessionAuthenticationFilterTest {
	private static final String FILE_ID = "52cce541-8752-4c00-9b91-452272a5d98f";
	private static final String IMAGE_PATH = "/assets/files/" + FILE_ID;
	private static final String SESSION_ID = "session-1";
	private static final String USER_ID = "USER-0001";

	private RefreshSessionStore refreshSessionStore;
	private ImageSessionAuthenticationFilter imageSessionAuthenticationFilter;

	@BeforeEach
	void setUp() {
		refreshSessionStore = mock(RefreshSessionStore.class);
		imageSessionAuthenticationFilter = new ImageSessionAuthenticationFilter(refreshSessionStore);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldNotFilter_nonImagePaths_shouldReturnTrue() {
		assertThat(imageSessionAuthenticationFilter.shouldNotFilter(imageRequest("/travels"))).isTrue();
		assertThat(imageSessionAuthenticationFilter.shouldNotFilter(imageRequest(IMAGE_PATH + "/download"))).isTrue();
	}

	@Test
	void shouldNotFilter_imageLookupPath_shouldReturnFalse() {
		assertThat(imageSessionAuthenticationFilter.shouldNotFilter(imageRequest(IMAGE_PATH))).isFalse();
	}

	@Test
	void doFilterInternal_sameSiteImageRequestWithSession_shouldAuthenticateWithImageReadOnly() throws Exception {
		givenLiveSession();

		doFilter(imageRequest(IMAGE_PATH));

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(USER_ID);
		assertThat(authentication.getAuthorities())
			.extracting(GrantedAuthority::getAuthority)
			.containsExactly(AuthConstant.IMAGE_READ_AUTHORITY);
	}

	@Test
	void doFilterInternal_missingFetchMetadataHeaders_shouldStillAuthenticate() throws Exception {
		givenLiveSession();

		doFilter(imageRequest(IMAGE_PATH, null, null));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	@Test
	void doFilterInternal_crossSiteRequest_shouldNotAuthenticate() throws Exception {
		doFilter(imageRequest(IMAGE_PATH, "cross-site", "image"));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(refreshSessionStore);
	}

	@Test
	void doFilterInternal_topLevelNavigation_shouldNotAuthenticate() throws Exception {
		doFilter(imageRequest(IMAGE_PATH, "same-site", "document"));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(refreshSessionStore);
	}

	@Test
	void doFilterInternal_missingSessionCookie_shouldNotAuthenticate() throws Exception {
		doFilter(new MockHttpServletRequest("GET", IMAGE_PATH));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verifyNoInteractions(refreshSessionStore);
	}

	@Test
	void doFilterInternal_unknownSession_shouldNotAuthenticate() throws Exception {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

		doFilter(imageRequest(IMAGE_PATH));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterInternal_expiredSession_shouldNotAuthenticate() throws Exception {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenReturn(Optional.of(new RefreshSession(
			SESSION_ID, USER_ID, "hash", System.currentTimeMillis() - 2000, System.currentTimeMillis() - 1000)));

		doFilter(imageRequest(IMAGE_PATH));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterInternal_corruptedSession_shouldNotAuthenticateAndShouldNotPropagate() throws Exception {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenThrow(new InvalidRefreshTokenException());

		doFilter(imageRequest(IMAGE_PATH));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterInternal_alreadyAuthenticated_shouldKeepExistingAuthentication() throws Exception {
		Authentication existing = new UsernamePasswordAuthenticationToken(
			"jwt-user", "", java.util.List.of(new SimpleGrantedAuthority("USER")));
		SecurityContextHolder.getContext().setAuthentication(existing);

		doFilter(imageRequest(IMAGE_PATH));

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
		verifyNoInteractions(refreshSessionStore);
	}

	private void givenLiveSession() {
		when(refreshSessionStore.findBySessionId(SESSION_ID)).thenReturn(Optional.of(new RefreshSession(
			SESSION_ID, USER_ID, "hash", System.currentTimeMillis(), System.currentTimeMillis() + 60_000)));
	}

	private void doFilter(MockHttpServletRequest request) throws Exception {
		FilterChain chain = mock(FilterChain.class);
		MockHttpServletResponse response = new MockHttpServletResponse();

		imageSessionAuthenticationFilter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	private MockHttpServletRequest imageRequest(String path) {
		return imageRequest(path, "same-site", "image");
	}

	private MockHttpServletRequest imageRequest(String path, String fetchSite, String fetchDest) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setCookies(new Cookie(AuthConstant.SESSION_ID_COOKIE_NAME, SESSION_ID));
		if (fetchSite != null) {
			request.addHeader("Sec-Fetch-Site", fetchSite);
		}
		if (fetchDest != null) {
			request.addHeader("Sec-Fetch-Dest", fetchDest);
		}

		return request;
	}
}
