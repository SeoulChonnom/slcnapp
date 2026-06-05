package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizOptionJpo;
import com.seoulchonnom.spec.trip.entity.vo.Option;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface TripQuizOptionJpoMapper {
	Option toDomain(TripQuizOptionJpo tripQuizOptionJpo);

	@Mapping(target = "quiz", ignore = true)
	TripQuizOptionJpo toJpo(Option option);

	default List<Option> toDomainList(List<TripQuizOptionJpo> tripQuizOptionJpoList) {
		if (tripQuizOptionJpoList == null) {
			return List.of();
		}

		return tripQuizOptionJpoList.stream()
			.sorted(Comparator.comparingInt(TripQuizOptionJpo::getSortOrder))
			.map(this::toDomain)
			.toList();
	}
}
