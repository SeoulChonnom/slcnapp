package com.seoulchonnom.spec.user.entity;

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
	private List<Authority> authorityList;
}
