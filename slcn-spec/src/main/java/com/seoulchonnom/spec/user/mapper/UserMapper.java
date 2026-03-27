package com.seoulchonnom.spec.user.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

@Component
public class UserMapper {
	public UserRdo toUserRdo(User user, String accessToken) {
		return UserRdo.builder()
			.accessToken(accessToken)
			.username(user.getUsername())
			.name(user.getName())
			.roleList(user.getAuthorityList().stream().map(authority -> authority.getRole()).toList())
			.build();
	}
}
