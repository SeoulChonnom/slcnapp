package com.seoulchonnom.spec.user.entity;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

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
	private List<Authority> authorityList;

	public User(UserCdo userCdo, String id, String password) {
		super(id);
		User.builder()
			.name(userCdo.getName())
			.username(userCdo.getUserName())
			.password(password)
			.authorityList(new ArrayList<>())
			.build();
		this.authorityList.add(new Authority());
	}

	public UserRdo toRdo(String token) {
		return UserRdo.builder()
			.accessToken(token)
			.username(this.username)
			.name(this.name)
			.roleList(this.authorityList.stream().map(Authority::getRole).toList())
			.build();
	}
}
