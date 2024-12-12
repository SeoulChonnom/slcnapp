package com.seoulchonnom.slcnapp.user.dto;

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
}
