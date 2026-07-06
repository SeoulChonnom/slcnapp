package com.seoulchonnom.spec.user.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UserLoginTest {
	private static final int LOGIN_FAIL_LIMIT_COUNT = 5;
	private static final long CLEAR_TIME_MILLIS = 300_000L;
	private static final long FIXED_NOW = 1_700_000_000_000L;

	@Test
	void markLoginFailure_shouldIncrementFailCountAndSetFailTime() {
		UserLogin userLogin = UserLogin.newUser("USER-0001");

		userLogin.markLoginFailure(FIXED_NOW);

		assertThat(userLogin.getLoginFailCount()).isEqualTo(1);
		assertThat(userLogin.getLastLoginFailTime()).isEqualTo(FIXED_NOW);
	}

	@Test
	void markLoginFailure_shouldTriggerBlockedStateWhenFailCountReachesLimit() {
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT - 1, FIXED_NOW - 1_000L);

		userLogin.markLoginFailure(FIXED_NOW);

		assertThat(userLogin.getLoginFailCount()).isEqualTo(LOGIN_FAIL_LIMIT_COUNT);
		assertThat(userLogin.isLoginBlocked(LOGIN_FAIL_LIMIT_COUNT)).isTrue();
	}

	@Test
	void isLoginBlocked_shouldReturnFalseWhenFailCountBelowLimit() {
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT - 1, FIXED_NOW);

		assertThat(userLogin.isLoginBlocked(LOGIN_FAIL_LIMIT_COUNT)).isFalse();
	}

	@Test
	void isLoginBlockExpired_shouldReturnTrueExactlyAtExpiry() {
		long lastFailTime = FIXED_NOW - CLEAR_TIME_MILLIS;
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT, lastFailTime);

		assertThat(userLogin.isLoginBlockExpired(LOGIN_FAIL_LIMIT_COUNT, CLEAR_TIME_MILLIS, FIXED_NOW)).isTrue();
	}

	@Test
	void isLoginBlockExpired_shouldReturnFalseJustBeforeExpiry() {
		long lastFailTime = FIXED_NOW - CLEAR_TIME_MILLIS + 1;
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT, lastFailTime);

		assertThat(userLogin.isLoginBlockExpired(LOGIN_FAIL_LIMIT_COUNT, CLEAR_TIME_MILLIS, FIXED_NOW)).isFalse();
	}

	@Test
	void isLoginBlockExpired_shouldReturnTrueJustAfterExpiry() {
		long lastFailTime = FIXED_NOW - CLEAR_TIME_MILLIS - 1;
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT, lastFailTime);

		assertThat(userLogin.isLoginBlockExpired(LOGIN_FAIL_LIMIT_COUNT, CLEAR_TIME_MILLIS, FIXED_NOW)).isTrue();
	}

	@Test
	void isLoginBlockExpired_shouldReturnFalseWhenNotBlocked() {
		long lastFailTime = FIXED_NOW - CLEAR_TIME_MILLIS;
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT - 1, lastFailTime);

		assertThat(userLogin.isLoginBlockExpired(LOGIN_FAIL_LIMIT_COUNT, CLEAR_TIME_MILLIS, FIXED_NOW)).isFalse();
	}

	@Test
	void isLoginBlockExpired_shouldReturnFalseWhenFailTimeNotSet() {
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT, 0L);

		assertThat(userLogin.isLoginBlockExpired(LOGIN_FAIL_LIMIT_COUNT, CLEAR_TIME_MILLIS, FIXED_NOW)).isFalse();
	}

	@Test
	void markLoginSuccess_shouldResetFailCountAndSetLastLoginTime() {
		UserLogin userLogin = new UserLogin("USER-0001", 0L, 3, FIXED_NOW - 1_000L);

		userLogin.markLoginSuccess(FIXED_NOW);

		assertThat(userLogin.getLoginFailCount()).isZero();
		assertThat(userLogin.getLastLoginTime()).isEqualTo(FIXED_NOW);
	}

	@Test
	void clearLoginFailure_shouldResetFailCountAndFailTime() {
		UserLogin userLogin = new UserLogin("USER-0001", 0L, LOGIN_FAIL_LIMIT_COUNT, FIXED_NOW);

		userLogin.clearLoginFailure();

		assertThat(userLogin.getLoginFailCount()).isZero();
		assertThat(userLogin.getLastLoginFailTime()).isZero();
	}
}
