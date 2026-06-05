package com.seoulchonnom.spec.user.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.user.entity.Authority;
import com.seoulchonnom.spec.user.entity.Role;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

@Mapper(componentModel = SPRING)
public interface UserMapper {
	@Mapping(target = "accessToken", source = "accessToken")
	@Mapping(target = "username", source = "user.username")
	@Mapping(target = "name", source = "user.name")
	@Mapping(target = "roleList", source = "user.authorityList")
	UserRdo toUserRdo(User user, String accessToken);

	default List<Role> map(List<Authority> authorityList) {
		if (authorityList == null) {
			return List.of();
		}

		return authorityList.stream()
			.map(Authority::getRole)
			.toList();
	}
}
