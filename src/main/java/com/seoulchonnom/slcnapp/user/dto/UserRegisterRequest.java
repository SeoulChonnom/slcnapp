package com.seoulchonnom.slcnapp.user.dto;

import com.seoulchonnom.slcnapp.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterRequest {
	String name;
	String userName;
	String password;

	public User from(String encodePassword) {
		return User.builder()
			.name(name)
			.username(userName)
			.password(encodePassword)
			.build();
	}
}
