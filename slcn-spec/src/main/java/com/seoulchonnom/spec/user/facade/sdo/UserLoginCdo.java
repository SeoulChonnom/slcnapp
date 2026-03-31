package com.seoulchonnom.spec.user.facade.sdo;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginCdo {
	@JsonAlias("userName")
	String username;
	String password;
}
