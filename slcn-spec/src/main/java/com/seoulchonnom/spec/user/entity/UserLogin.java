package com.seoulchonnom.spec.user.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLogin {
	private String userId;
	private long lastLoginTime;

	private int loginFailCount;
	private long lastLoginFailTime;

	public static UserLogin newUser(String userId) {
		return new UserLogin(userId, 0L, 0, 0L);
	}

	public boolean isLoginBlocked(int loginFailLimitCount) {
		return this.loginFailCount >= loginFailLimitCount;
	}

	public boolean isLoginBlockExpired(int loginFailLimitCount, long clearTimeMillis, long now) {
		return isLoginBlocked(loginFailLimitCount)
			&& this.lastLoginFailTime > 0
			&& now - this.lastLoginFailTime >= clearTimeMillis;
	}

	public void clearLoginFailure() {
		this.loginFailCount = 0;
		this.lastLoginFailTime = 0L;
	}

	public void markLoginFailure(long now) {
		this.loginFailCount = this.loginFailCount + 1;
		this.lastLoginFailTime = now;
	}

	public void markLoginSuccess(long now) {
		this.loginFailCount = 0;
		this.lastLoginTime = now;
	}
}
