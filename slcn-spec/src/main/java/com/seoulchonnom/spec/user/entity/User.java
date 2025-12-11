package com.seoulchonnom.spec.user.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class User extends DomainEntity {
	private String username;
	private String name;
	private String password;
	private List<Authority> authorityList = new ArrayList<>();
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
