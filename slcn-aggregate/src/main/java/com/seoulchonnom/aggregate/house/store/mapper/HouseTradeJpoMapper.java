package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseTradeJpo;
import com.seoulchonnom.spec.house.entity.HouseTrade;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseTradeJpoMapper {
	HouseTradeJpo toJpo(HouseTrade domain);

	HouseTrade toDomain(HouseTradeJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseTradeJpo jpo, @MappingTarget HouseTrade domain) {
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
