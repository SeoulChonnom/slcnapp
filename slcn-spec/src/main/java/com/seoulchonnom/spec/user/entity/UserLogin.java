package com.seoulchonnom.spec.user.entity;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class UserLogin {
	private String userId;
	private LocalDateTime lastLoginTime;

	private int loginFailCount;
	private LocalDateTime lastLoginFailTime;

	public void updateLoginFailCount() {
		this.loginFailCount = this.loginFailCount + 1;
		this.lastLoginFailTime = LocalDateTime.now();
	}

	public void resetLoginFailCount() {
		this.loginFailCount = 0;
		this.lastLoginTime = LocalDateTime.now();
	}
}
