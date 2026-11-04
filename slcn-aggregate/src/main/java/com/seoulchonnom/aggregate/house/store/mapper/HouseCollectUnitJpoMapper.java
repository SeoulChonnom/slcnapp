package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseCollectUnitJpo;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseCollectUnitJpoMapper {
	HouseCollectUnitJpo toJpo(HouseCollectUnit domain);

	HouseCollectUnit toDomain(HouseCollectUnitJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseCollectUnitJpo jpo, @MappingTarget HouseCollectUnit domain) {
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
