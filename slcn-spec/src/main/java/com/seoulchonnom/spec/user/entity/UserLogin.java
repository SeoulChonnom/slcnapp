package com.seoulchonnom.spec.user.entity;

import lombok.Getter;

@Getter
public class UserLogin {
	private String userId;
	private long lastLoginTime;

	private int loginFailCount;
	private long lastLoginFailTime;

	public void updateLoginFailCount() {
		this.loginFailCount = this.loginFailCount + 1;
		this.lastLoginFailTime = System.currentTimeMillis();
	}

	public void resetLoginFailCount() {
		this.loginFailCount = 0;
		this.lastLoginTime = System.currentTimeMillis();
	}
}
