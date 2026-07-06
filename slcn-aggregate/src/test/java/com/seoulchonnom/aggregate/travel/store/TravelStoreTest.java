package com.seoulchonnom.aggregate.travel.store;

import static org.mockito.Mockito.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelJpo;
import com.seoulchonnom.aggregate.travel.store.mapper.TravelJpoMapper;
import com.seoulchonnom.aggregate.travel.store.repository.TravelRepository;
import com.seoulchonnom.spec.travel.entity.Travel;

class TravelStoreTest {
	private final TravelRepository travelRepository = mock(TravelRepository.class);
	private final TravelJpoMapper travelJpoMapper = mock(TravelJpoMapper.class);
	private final TravelStore travelStore = new TravelStore(travelRepository, travelJpoMapper);

	@Test
	void deleteTravel_shouldDeleteOnlyTravelRoot() {
		Travel travel = Travel.builder()
			.title("여행")
			.region("강릉")
			.startDate(LocalDate.of(2026, 8, 1))
			.endDate(LocalDate.of(2026, 8, 2))
			.build();
		travel.setId("travel-1");
		TravelJpo travelJpo = new TravelJpo();
		when(travelJpoMapper.toJpo(travel)).thenReturn(travelJpo);

		travelStore.deleteTravel(travel);

		verify(travelRepository).delete(travelJpo);
	}
}
