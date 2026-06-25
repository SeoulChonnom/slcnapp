package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface TripJpoMapper {
	Trip toDomain(TripJpo tripJpo);

	TripJpo toJpo(Trip trip);
}
