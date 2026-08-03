package com.seoulchonnom.auth.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.seoulchonnom.auth.store.projection.ClientPrincipal;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationStatus;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;

class JwtAuthenticationFilterTest {

	private JwtTokenProvider jwtTokenProvider;
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = mock(JwtTokenProvider.class);
		jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldNotFilter_publicAuthPaths_shouldReturnTrue() {
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/users/login"))).isTrue();
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/users/token"))).isTrue();
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/users/logout"))).isTrue();
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/clients/token"))).isTrue();
	}

	@Test
	void shouldNotFilter_protectedPaths_shouldReturnFalse() {
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/users/register"))).isFalse();
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/calendars"))).isFalse();
		assertThat(jwtAuthenticationFilter.shouldNotFilter(requestFor("/trips"))).isFalse();
	}

	@Test
	void doFilterInternal_missingToken_shouldContinueChainWithoutAuthentication() throws Exception {
		MockHttpServletRequest request = requestFor("/trips");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);
		when(jwtTokenProvider.resolveToken(request)).thenReturn(null);
		when(jwtTokenProvider.validateAccessToken(null))
			.thenReturn(TokenValidationResult.invalid(TokenValidationStatus.MISSING, null));

		jwtAuthenticationFilter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterInternal_revokedCredentialVersion_shouldContinueWithoutAuthentication() throws Exception {
		MockHttpServletRequest request = requestFor("/users/me");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);
		Claims claims = mock(Claims.class);
		when(jwtTokenProvider.resolveToken(request)).thenReturn("access-token");
		when(jwtTokenProvider.validateAccessToken("access-token")).thenReturn(TokenValidationResult.valid(claims));
		when(jwtTokenProvider.getAuthentication(claims))
			.thenThrow(new IllegalArgumentException("Credential version mismatch."));

		jwtAuthenticationFilter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilterInternal_clientToken_shouldSetClientAuthentication() throws Exception {
		MockHttpServletRequest request = requestFor("/trips");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);
		Claims claims = mock(Claims.class);
		ClientPrincipal principal = new ClientPrincipal("CRON-001", "schedule-cron");
		var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
			principal, "", principal.getAuthorities());
		when(jwtTokenProvider.resolveToken(request)).thenReturn("client-access-token");
		when(jwtTokenProvider.validateAccessToken("client-access-token")).thenReturn(
			TokenValidationResult.valid(claims));
		when(jwtTokenProvider.getAuthentication(claims)).thenReturn(authentication);

		jwtAuthenticationFilter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(principal);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
			.extracting("authority")
			.containsExactly("CLIENT");
	}

	private MockHttpServletRequest requestFor(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setServletPath(path);
		return request;
	}
}
