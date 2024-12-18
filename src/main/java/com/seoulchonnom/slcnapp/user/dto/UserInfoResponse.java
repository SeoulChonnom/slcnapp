package com.seoulchonnom.slcnapp.user.dto;

import java.util.List;

import com.seoulchonnom.slcnapp.user.domain.Role;
import com.seoulchonnom.slcnapp.user.domain.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserInfoResponse {
	private String accessToken;
	private String username;
	private String name;
	private List<Role> roleList;

	public static UserInfoResponse of(String accessToken, User user) {
		return UserInfoResponse.builder()
			.accessToken(accessToken)
			.username(user.getUsername())
			.name(user.getName())
			.roleList(user.getAuthorityList().stream().map((a) -> a.getRole()).toList())
			.build();
	}
}