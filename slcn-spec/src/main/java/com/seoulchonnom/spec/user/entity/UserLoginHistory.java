package com.seoulchonnom.spec.user.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginHistory {
	private String userId;
	private long loginTime;
	private boolean loginSuccess;

	public static UserLoginHistory create(String userId, boolean loginSuccess) {
		return new UserLoginHistory(userId, System.currentTimeMillis(), loginSuccess);
	}
}
