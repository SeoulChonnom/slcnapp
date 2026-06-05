package com.seoulchonnom.aggregate.user.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;
import com.seoulchonnom.spec.user.entity.User;

@Mapper(componentModel = SPRING, uses = AuthorityJpoMapper.class, builder = @Builder(disableBuilder = true))
public interface UserJpoMapper {

	@Mapping(target = "authorityList", source = "authorityList")
	UserJpo toJpo(User user);

	@Mapping(target = "authorityList", source = "authorityList")
	User toDomain(UserJpo userJpo);

	@AfterMapping
	default void mapInheritedFields(UserJpo userJpo, @MappingTarget User user) {
		user.setId(userJpo.getId());
		user.setEntityVersion(userJpo.getEntityVersion());
		if (userJpo.getRegisteredTime() != null) {
			user.setRegisteredTime(userJpo.getRegisteredTime());
		}
		if (userJpo.getModifiedTime() != null) {
			user.setModifiedTime(userJpo.getModifiedTime());
		}
	}
}
