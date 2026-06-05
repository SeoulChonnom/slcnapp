package com.seoulchonnom.auth.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.auth.logic.UserAuthDetailLogic;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;

class JwtTokenProviderTest {

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(mock(UserAuthDetailLogic.class));
		ReflectionTestUtils.setField(jwtTokenProvider, "key", encodedSecretKey());
		ReflectionTestUtils.setField(jwtTokenProvider, "algorithm", "HS512");
		ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "slcnapp");
		ReflectionTestUtils.setField(jwtTokenProvider, "accessAudiences", List.of("slcn-platform"));
		ReflectionTestUtils.setField(jwtTokenProvider, "refreshAudience", "slcn-auth-refresh");
		ReflectionTestUtils.invokeMethod(jwtTokenProvider, "init");
	}

	@Test
	void createTokenAddsClaimsForExternalValidation() {
		UserDetails userDetails = User.withUsername("tester")
			.password("encoded")
			.authorities("USER")
			.build();

		TokenRdo tokenRdo = jwtTokenProvider.createToken(userDetails, "USER-001");
		JwtTokenProvider.TokenValidationResult accessValidation = jwtTokenProvider.validateAccessToken(
			tokenRdo.getAccessToken());
		JwtTokenProvider.TokenValidationResult refreshValidation = jwtTokenProvider.validateRefreshToken(
			tokenRdo.getRefreshToken());

		assertThat(accessValidation.valid()).isTrue();
		assertThat(accessValidation.claims().getSubject()).isEqualTo("USER-001");
		assertThat(accessValidation.claims().get("username", String.class)).isEqualTo("tester");
		assertThat(accessValidation.claims().get("userName", String.class)).isEqualTo("tester");
		assertThat(accessValidation.claims().get("token_type", String.class)).isEqualTo("access");
		assertThat(accessValidation.claims().get("roles", List.class)).contains("USER");
		assertThat(accessValidation.claims()).containsKey("aud");

		assertThat(refreshValidation.valid()).isTrue();
		assertThat(refreshValidation.claims().getSubject()).isEqualTo("USER-001");
		assertThat(refreshValidation.claims().get("token_type", String.class)).isEqualTo("refresh");
		assertThat(refreshValidation.claims()).containsKey("aud");
	}

	@Test
	void accessAndRefreshValidationRejectWrongTokenType() {
		UserDetails userDetails = User.withUsername("tester")
			.password("encoded")
			.authorities("USER")
			.build();

		TokenRdo tokenRdo = jwtTokenProvider.createToken(userDetails, "USER-001");

		assertThat(jwtTokenProvider.validateAccessToken(tokenRdo.getRefreshToken()).status())
			.isEqualTo(JwtTokenProvider.TokenValidationStatus.INVALID_TOKEN_TYPE);
		assertThat(jwtTokenProvider.validateRefreshToken(tokenRdo.getAccessToken()).status())
			.isEqualTo(JwtTokenProvider.TokenValidationStatus.INVALID_TOKEN_TYPE);
	}

	private String encodedSecretKey() {
		byte[] secret = "slcn-platform-shared-secret-key-for-hs512-validation-2026-with-extra-length-for-tests"
			.getBytes(StandardCharsets.UTF_8);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
	}
}
