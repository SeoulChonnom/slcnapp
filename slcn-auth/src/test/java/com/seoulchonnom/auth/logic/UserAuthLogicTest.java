package com.seoulchonnom.auth.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.exception.UserLoginFailCountOverException;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.store.UserAuthStore;
import com.seoulchonnom.auth.store.projection.RefreshSession;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;
import com.seoulchonnom.auth.util.RefreshTokenHasher;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.entity.UserLogin;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;

import io.jsonwebtoken.Claims;

class UserAuthLogicTest {
	private final UserAuthStore userAuthStore = mock(UserAuthStore.class);
	private final RefreshSessionStore refreshSessionStore = mock(RefreshSessionStore.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
	private final PasswordGenerator passwordGenerator = mock(PasswordGenerator.class);

	private UserAuthLogic userAuthLogic;

	@BeforeEach
	void setUp() {
			userAuthLogic = new UserAuthLogic(userAuthStore, refreshSessionStore, jwtTokenProvider, refreshTokenHasher,
				passwordGenerator);
			ReflectionTestUtils.setField(userAuthLogic, "loginFailLimitCount", 5);
			ReflectionTestUtils.setField(userAuthLogic, "loginLimitClearTimeSeconds", 300L);
		}

	@Test
	void issueLoginToken_shouldResetFailCountAndStoreSuccessHistory() {
		User user = user("USER-0001", "tester", "encoded-password");
		UserLogin userLogin = new UserLogin("USER-0001", 0L, 3, 0L);
		UserLoginCdo userLoginCdo = UserLoginCdo.builder().username("tester").password("Password1!").build();
		UserDetail userDetail = new UserDetail(user);
		TokenRdo tokenRdo = TokenRdo.builder()
			.userId("USER-0001")
			.accessToken("access")
			.refreshToken("refresh")
			.build();
		when(userAuthStore.getUserDetail("tester")).thenReturn(userDetail);
		when(userAuthStore.getUserLogin("USER-0001")).thenReturn(userLogin);
		when(passwordGenerator.matches("Password1!", "encoded-password")).thenReturn(true);
		when(jwtTokenProvider.createToken(userDetail, "USER-0001")).thenReturn(tokenRdo);
		when(jwtTokenProvider.getRefreshTokenTtl()).thenReturn(Duration.ofDays(14));
		when(refreshTokenHasher.hash("refresh")).thenReturn("hashed-refresh");

		TokenSessionVo result = userAuthLogic.issueLoginToken(userLoginCdo);

		assertThat(result.tokenRdo()).isEqualTo(tokenRdo);
		assertThat(result.sessionId()).isNotBlank();
		ArgumentCaptor<UserLogin> userLoginCaptor = ArgumentCaptor.forClass(UserLogin.class);
		ArgumentCaptor<UserLoginHistory> historyCaptor = ArgumentCaptor.forClass(UserLoginHistory.class);
		ArgumentCaptor<RefreshSession> refreshSessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
		verify(userAuthStore).saveUserLogin(userLoginCaptor.capture());
		verify(userAuthStore).saveUserLoginHistory(historyCaptor.capture());
		verify(refreshSessionStore).save(refreshSessionCaptor.capture(), eq(Duration.ofDays(14)));
		assertThat(userLoginCaptor.getValue().getLoginFailCount()).isZero();
		assertThat(userLoginCaptor.getValue().getLastLoginTime()).isPositive();
		assertThat(historyCaptor.getValue().isLoginSuccess()).isTrue();
		assertThat(historyCaptor.getValue().getUserId()).isEqualTo("USER-0001");
		assertThat(refreshSessionCaptor.getValue().sessionId()).isEqualTo(result.sessionId());
		assertThat(refreshSessionCaptor.getValue().refreshTokenHash()).isEqualTo("hashed-refresh");
	}

	@Test
	void issueLoginToken_shouldIncreaseFailCountAndStoreFailureHistoryWhenPasswordDoesNotMatch() {
		User user = user("USER-0001", "tester", "encoded-password");
		UserLogin userLogin = UserLogin.newUser("USER-0001");
		UserLoginCdo userLoginCdo = UserLoginCdo.builder().username("tester").password("Password1!").build();
		when(userAuthStore.getUserDetail("tester")).thenReturn(new UserDetail(user));
		when(userAuthStore.getUserLogin("USER-0001")).thenReturn(userLogin);
		when(passwordGenerator.matches("Password1!", "encoded-password")).thenReturn(false);

		assertThatThrownBy(() -> userAuthLogic.issueLoginToken(userLoginCdo))
			.isInstanceOf(InvalidUserException.class);

		ArgumentCaptor<UserLogin> userLoginCaptor = ArgumentCaptor.forClass(UserLogin.class);
		ArgumentCaptor<UserLoginHistory> historyCaptor = ArgumentCaptor.forClass(UserLoginHistory.class);
		verify(userAuthStore).saveUserLogin(userLoginCaptor.capture());
		verify(userAuthStore).saveUserLoginHistory(historyCaptor.capture());
		assertThat(userLoginCaptor.getValue().getLoginFailCount()).isEqualTo(1);
		assertThat(userLoginCaptor.getValue().getLastLoginFailTime()).isPositive();
		assertThat(historyCaptor.getValue().isLoginSuccess()).isFalse();
		verify(jwtTokenProvider, never()).createToken(any(), anyString());
	}

	@Test
	void issueLoginToken_shouldRejectBlockedLoginBeforePasswordValidation() {
		User user = user("USER-0001", "tester", "encoded-password");
		UserLogin userLogin = new UserLogin("USER-0001", 0L, 5, 0L);
		UserLoginCdo userLoginCdo = UserLoginCdo.builder().username("tester").password("Password1!").build();
		when(userAuthStore.getUserDetail("tester")).thenReturn(new UserDetail(user));
		when(userAuthStore.getUserLogin("USER-0001")).thenReturn(userLogin);

		assertThatThrownBy(() -> userAuthLogic.issueLoginToken(userLoginCdo))
			.isInstanceOf(UserLoginFailCountOverException.class);

		ArgumentCaptor<UserLogin> userLoginCaptor = ArgumentCaptor.forClass(UserLogin.class);
		ArgumentCaptor<UserLoginHistory> historyCaptor = ArgumentCaptor.forClass(UserLoginHistory.class);
		verify(userAuthStore).saveUserLogin(userLoginCaptor.capture());
		verify(userAuthStore).saveUserLoginHistory(historyCaptor.capture());
		assertThat(userLoginCaptor.getValue().getLoginFailCount()).isEqualTo(6);
		assertThat(historyCaptor.getValue().isLoginSuccess()).isFalse();
		verify(passwordGenerator, never()).matches(any(), any());
	}

	@Test
	void issueLoginToken_shouldClearExpiredLoginBlockBeforePasswordValidation() {
		User user = user("USER-0001", "tester", "encoded-password");
		UserLogin userLogin = new UserLogin("USER-0001", 0L, 5, System.currentTimeMillis() - Duration.ofMinutes(6).toMillis());
		UserLoginCdo userLoginCdo = UserLoginCdo.builder().username("tester").password("Password1!").build();
		UserDetail userDetail = new UserDetail(user);
		TokenRdo tokenRdo = TokenRdo.builder()
			.userId("USER-0001")
			.accessToken("access")
			.refreshToken("refresh")
			.build();
		when(userAuthStore.getUserDetail("tester")).thenReturn(userDetail);
		when(userAuthStore.getUserLogin("USER-0001")).thenReturn(userLogin);
		when(passwordGenerator.matches("Password1!", "encoded-password")).thenReturn(true);
		when(jwtTokenProvider.createToken(userDetail, "USER-0001")).thenReturn(tokenRdo);
		when(jwtTokenProvider.getRefreshTokenTtl()).thenReturn(Duration.ofDays(14));
		when(refreshTokenHasher.hash("refresh")).thenReturn("hashed-refresh");

		userAuthLogic.issueLoginToken(userLoginCdo);

		ArgumentCaptor<UserLogin> userLoginCaptor = ArgumentCaptor.forClass(UserLogin.class);
		verify(userAuthStore).saveUserLogin(userLoginCaptor.capture());
		assertThat(userLoginCaptor.getValue().getLoginFailCount()).isZero();
		assertThat(userLoginCaptor.getValue().getLastLoginFailTime()).isZero();
	}

	@Test
	void reissueToken_shouldRotateRefreshTokenWhenRedisSessionMatches() {
		User user = user("USER-0001", "tester", "encoded-password");
		UserDetail userDetail = new UserDetail(user);
		TokenRdo tokenRdo = TokenRdo.builder()
			.userId("USER-0001")
			.accessToken("new-access")
			.refreshToken("new-refresh")
			.build();
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn("USER-0001");
		when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(TokenValidationResult.valid(claims));
		when(refreshSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(
			new RefreshSession("session-1", "USER-0001", "hashed-refresh-token", 1L, 2L)));
		when(refreshTokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");
		when(refreshTokenHasher.hash("new-refresh")).thenReturn("hashed-new-refresh");
		when(userAuthStore.getUserDetailById("USER-0001")).thenReturn(userDetail);
		when(jwtTokenProvider.createToken(userDetail, "USER-0001")).thenReturn(tokenRdo);
		when(jwtTokenProvider.getRefreshTokenTtl()).thenReturn(Duration.ofDays(14));

		TokenSessionVo result = userAuthLogic.reissueToken("refresh-token", "session-1");

		assertThat(result.sessionId()).isEqualTo("session-1");
		assertThat(result.tokenRdo()).isEqualTo(tokenRdo);
		ArgumentCaptor<RefreshSession> refreshSessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
		verify(refreshSessionStore).save(refreshSessionCaptor.capture(), eq(Duration.ofDays(14)));
		assertThat(refreshSessionCaptor.getValue().sessionId()).isEqualTo("session-1");
		assertThat(refreshSessionCaptor.getValue().refreshTokenHash()).isEqualTo("hashed-new-refresh");
	}

	@Test
	void reissueToken_shouldRejectWhenStoredSessionDoesNotMatch() {
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn("USER-0001");
		when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(TokenValidationResult.valid(claims));
		when(refreshSessionStore.findBySessionId("session-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userAuthLogic.reissueToken("refresh-token", "session-1"))
			.isInstanceOf(InvalidRefreshTokenException.class);

		verify(jwtTokenProvider, never()).createToken(any(), anyString());
	}

	@Test
	void logout_shouldDeleteStoredSession() {
		userAuthLogic.logout("session-1");

		verify(refreshSessionStore).delete("session-1");
	}

	private User user(String id, String username, String password) {
		User user = User.builder()
			.username(username)
			.name("tester")
			.password(password)
			.authorityList(new ArrayList<>())
			.build();
		user.setId(id);
		return user;
	}
}
