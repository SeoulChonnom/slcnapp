package com.seoulchonnom.spec.user.facade.sdo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCdo {
	String name;

	@JsonProperty("username")
	@JsonAlias("userName")
	String username;

	String password;
}
