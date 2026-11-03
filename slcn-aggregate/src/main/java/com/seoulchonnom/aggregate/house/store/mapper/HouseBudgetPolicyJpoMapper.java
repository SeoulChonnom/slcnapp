package com.seoulchonnom.aggregate.house.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBudgetPolicyJpo;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseBudgetPolicyJpoMapper {
	HouseBudgetPolicyJpo toJpo(HouseBudgetPolicy policy);

	HouseBudgetPolicy toDomain(HouseBudgetPolicyJpo jpo);

	@AfterMapping
	default void mapInheritedFields(HouseBudgetPolicyJpo jpo, @MappingTarget HouseBudgetPolicy policy) {
		policy.setId(jpo.getId());
		policy.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			policy.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			policy.setModifiedTime(jpo.getModifiedTime());
		}
	}
}
