package com.seoulchonnom.aggregate.user.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;
import com.seoulchonnom.spec.user.entity.User;

@Mapper(componentModel = SPRING, uses = AuthorityJpoMapper.class)
public interface UserJpoMapper {

	@Mapping(target = "authorityList", source = "authorityList")
	UserJpo toJpo(User user);

	@Mapping(target = "authorityList", source = "authorityList")
	User toDomain(UserJpo userJpo);
}
