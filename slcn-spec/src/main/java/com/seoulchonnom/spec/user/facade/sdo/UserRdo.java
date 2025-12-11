package com.seoulchonnom.spec.user.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.user.entity.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserRdo {
	private String accessToken;
	private String username;
	private String name;
	private List<Role> roleList;
}
