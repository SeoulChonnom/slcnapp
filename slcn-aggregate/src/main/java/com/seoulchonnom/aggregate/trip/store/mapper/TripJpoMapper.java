package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@Mapper(componentModel = SPRING, uses = TripQuizJpoMapper.class, builder = @Builder(disableBuilder = true))
public interface TripJpoMapper {
	Trip toDomain(TripJpo tripJpo);

	TripJpo toJpo(Trip trip);

	@AfterMapping
	default void mapInheritedFields(TripJpo tripJpo, @MappingTarget Trip trip) {
		trip.setId(tripJpo.getId());
		trip.setEntityVersion(tripJpo.getEntityVersion());
		if (tripJpo.getRegisteredTime() != null) {
			trip.setRegisteredTime(tripJpo.getRegisteredTime());
		}
		if (tripJpo.getModifiedTime() != null) {
			trip.setModifiedTime(tripJpo.getModifiedTime());
		}
	}

	@AfterMapping
	default void linkTripQuiz(@MappingTarget TripJpo tripJpo) {
		TripQuizJpo tripQuizJpo = tripJpo.getQuiz();
		if (tripQuizJpo == null) {
			return;
		}

		tripQuizJpo.setTrip(tripJpo);
	}
}
