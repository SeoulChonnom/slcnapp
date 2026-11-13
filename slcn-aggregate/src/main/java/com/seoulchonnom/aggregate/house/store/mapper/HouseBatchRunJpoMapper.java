package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBatchRunJpo;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseBatchRunJpoMapper {
	HouseBatchRunJpo toJpo(HouseBatchRun domain);

	HouseBatchRun toDomain(HouseBatchRunJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseBatchRunJpo jpo, @MappingTarget HouseBatchRun domain) {
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
