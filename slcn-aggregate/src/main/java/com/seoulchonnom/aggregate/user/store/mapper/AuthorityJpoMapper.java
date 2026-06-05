package com.seoulchonnom.aggregate.user.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.Mapper;

import com.seoulchonnom.aggregate.user.store.jpo.AuthorityJpo;
import com.seoulchonnom.spec.user.entity.Authority;

@Mapper(componentModel = SPRING)
public interface AuthorityJpoMapper {
	Authority toDomain(AuthorityJpo authorityJpo);

	AuthorityJpo toJpo(Authority authority);

	List<Authority> toDomainList(List<AuthorityJpo> authorityJpoList);

	List<AuthorityJpo> toJpoList(List<Authority> authorityList);
}
