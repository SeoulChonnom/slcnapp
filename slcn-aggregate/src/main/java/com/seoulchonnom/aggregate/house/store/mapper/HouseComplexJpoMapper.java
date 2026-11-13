package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexJpo;
import com.seoulchonnom.spec.house.entity.HouseComplex;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseComplexJpoMapper {
	HouseComplexJpo toJpo(HouseComplex domain);

	HouseComplex toDomain(HouseComplexJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseComplexJpo jpo, @MappingTarget HouseComplex domain) {
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
