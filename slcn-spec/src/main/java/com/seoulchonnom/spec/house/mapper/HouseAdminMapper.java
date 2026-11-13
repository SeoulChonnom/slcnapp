package com.seoulchonnom.spec.house.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true), imports = HouseConstant.class)
public interface HouseAdminMapper {
	@Mapping(target = "complexId", source = "complex.id")
	@Mapping(target = "aptSeq", source = "complex.aptSeq")
	@Mapping(target = "name", source = "complex.aptName")
	@Mapping(target = "districtName", expression = "java(HouseConstant.districtName(complex.getLawdCd()))")
	@Mapping(target = "umdName", source = "complex.umdName")
	@Mapping(target = "bonbun", source = "complex.bonbun", qualifiedByName = "stripZeros")
	@Mapping(target = "bubun", source = "complex.bubun", qualifiedByName = "stripZeros")
	@Mapping(target = "roadAddress", source = "complex.roadAddress")
	@Mapping(target = "addressOverride", source = "complex.addressOverride")
	@Mapping(target = "geocodeStatus", source = "complex.geocodeStatus")
	@Mapping(target = "registerStatus", source = "complex.registerStatus")
	@Mapping(target = "trades12m", source = "trades12m")
	IncompleteComplexRdo toIncompleteComplexRdo(HouseComplex complex, long trades12m);

	@Mapping(target = "bonbun", source = "bonbun", qualifiedByName = "stripZeros")
	@Mapping(target = "bubun", source = "bubun", qualifiedByName = "stripZeros")
	AddressOverrideRdo toAddressOverrideRdo(AddressOverride override);

	/**
	 * 0 채움 4자리를 앞 0 없이 돌려준다. 전부 0이면 "0"이다.
	 */
	@Named("stripZeros")
	default String stripZeros(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.replaceFirst("^0+", "");
		return stripped.isEmpty() ? "0" : stripped;
	}
}
