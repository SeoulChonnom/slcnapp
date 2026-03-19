package com.seoulchonnom.spec.user.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Authority {
	private String userId;
	private Role role;
	private long registeredTime;

	public Authority(Role role) {
		this.role = role;
		this.registeredTime = System.currentTimeMillis();
	}

	public Authority() {
		this.role = Role.USER;
		this.registeredTime = System.currentTimeMillis();
	}
}
