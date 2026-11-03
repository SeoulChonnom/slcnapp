package com.seoulchonnom.spec.house.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface HouseBudgetPolicyMapper {
	@Mapping(target = "policyId", source = "id")
	@Mapping(target = "ltv", ignore = true)
	BudgetPolicyRdo toBudgetPolicyRdo(HouseBudgetPolicy policy);

	@AfterMapping
	default void mapLtv(HouseBudgetPolicy policy, @MappingTarget BudgetPolicyRdo rdo) {
		Map<String, BigDecimal> ltv = new LinkedHashMap<>();
		ltv.put("FIRST_TIME", policy.getLtvFirstTime());
		ltv.put("NON_FIRST", policy.getLtvNonFirst());
		rdo.setLtv(ltv);
	}
}
