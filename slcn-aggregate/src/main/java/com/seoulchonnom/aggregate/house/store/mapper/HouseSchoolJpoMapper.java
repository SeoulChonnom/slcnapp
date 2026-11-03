package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseSchoolJpo;
import com.seoulchonnom.spec.house.entity.HouseSchool;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseSchoolJpoMapper {
	HouseSchoolJpo toJpo(HouseSchool domain);

	HouseSchool toDomain(HouseSchoolJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseSchoolJpo jpo, @MappingTarget HouseSchool domain) {
		domain.setId(jpo.getId());
		domain.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			domain.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			domain.setModifiedTime(jpo.getModifiedTime());
		}
	}
}
