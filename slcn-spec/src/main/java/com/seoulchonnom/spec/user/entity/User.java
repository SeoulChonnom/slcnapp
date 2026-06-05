package com.seoulchonnom.spec.user.entity;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class User extends DomainEntity {
	private String username;
	private String name;
	private String password;
	private List<Authority> authorityList;

	public User(UserCdo userCdo, String id, String password) {
		super(id);
		this.name = userCdo.getName();
		this.username = userCdo.getUsername();
		this.password = password;
		this.authorityList = new ArrayList<>();
		this.authorityList.add(new Authority());
	}
}
