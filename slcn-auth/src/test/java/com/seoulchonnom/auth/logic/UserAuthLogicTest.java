package com.seoulchonnom.auth.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.exception.UserLoginFailCountOverException;
import com.seoulchonnom.auth.store.UserAuthStore;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.entity.UserLogin;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;

class UserAuthLogicTest {
	private final UserAuthStore userAuthStore = mock(UserAuthStore.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final PasswordGenerator passwordGenerator = mock(PasswordGenerator.class);

	private UserAuthLogic userAuthLogic;

	@BeforeEach
	void setUp() {
		userAuthLogic = new UserAuthLogic(userAuthStore, jwtTokenProvider, passwordGenerator);
		ReflectionTestUtils.setField(userAuthLogic, "loginFailLimitCount", 5);
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

		TokenRdo result = userAuthLogic.issueLoginToken(userLoginCdo);

		assertThat(result).isEqualTo(tokenRdo);
		ArgumentCaptor<UserLogin> userLoginCaptor = ArgumentCaptor.forClass(UserLogin.class);
		ArgumentCaptor<UserLoginHistory> historyCaptor = ArgumentCaptor.forClass(UserLoginHistory.class);
		verify(userAuthStore).saveUserLogin(userLoginCaptor.capture());
		verify(userAuthStore).saveUserLoginHistory(historyCaptor.capture());
		assertThat(userLoginCaptor.getValue().getLoginFailCount()).isZero();
		assertThat(userLoginCaptor.getValue().getLastLoginTime()).isPositive();
		assertThat(historyCaptor.getValue().isLoginSuccess()).isTrue();
		assertThat(historyCaptor.getValue().getUserId()).isEqualTo("USER-0001");
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
