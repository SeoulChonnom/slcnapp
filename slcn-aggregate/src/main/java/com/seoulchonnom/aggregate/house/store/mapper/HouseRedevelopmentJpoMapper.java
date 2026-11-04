package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseRedevelopmentJpo;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseRedevelopmentJpoMapper {
	HouseRedevelopmentJpo toJpo(HouseRedevelopment domain);

	HouseRedevelopment toDomain(HouseRedevelopmentJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseRedevelopmentJpo jpo, @MappingTarget HouseRedevelopment domain) {
		domain.setId(jpo.getId());
		domain.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			domain.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			domain.setModifiedTime(jpo.getModifiedTime());
		}
		// 컬럼이 나중에 추가되어 비어 있는 행은 아직 좌표 전인 것으로 본다.
		if (domain.getGeocodeStatus() == null) {
			domain.setGeocodeStatus(GeocodeStatus.PENDING);
		}
	}
}
