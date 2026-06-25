package com.seoulchonnom.aggregate.travel.store;

import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelDayJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.TravelPhotoJpo;
import com.seoulchonnom.aggregate.travel.store.mapper.TravelJpoMapper;
import com.seoulchonnom.aggregate.travel.store.repository.TravelDayRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelPhotoRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelPlaceRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelReviewRepository;
import com.seoulchonnom.aggregate.travel.store.repository.TravelTagRepository;
import com.seoulchonnom.spec.travel.entity.Travel;
import com.seoulchonnom.spec.travel.entity.TravelDay;

class TravelStoreTest {
	private final TravelRepository travelRepository = mock(TravelRepository.class);
	private final TravelDayRepository travelDayRepository = mock(TravelDayRepository.class);
	private final TravelPlaceRepository travelPlaceRepository = mock(TravelPlaceRepository.class);
	private final TravelPhotoRepository travelPhotoRepository = mock(TravelPhotoRepository.class);
	private final TravelTagRepository travelTagRepository = mock(TravelTagRepository.class);
	private final TravelReviewRepository travelReviewRepository = mock(TravelReviewRepository.class);
	private final TravelJpoMapper travelJpoMapper = mock(TravelJpoMapper.class);
	private final TravelStore travelStore = new TravelStore(travelRepository, travelDayRepository, travelPlaceRepository,
		travelPhotoRepository, travelTagRepository, travelReviewRepository, travelJpoMapper);

	@Test
	void deleteTravel_shouldDeleteAllPhotoLinksByTravelIdIncludingTravelLevelPhotos() {
		Travel travel = Travel.builder()
			.title("여행")
			.region("강릉")
			.startDate(LocalDate.of(2026, 8, 1))
			.endDate(LocalDate.of(2026, 8, 2))
			.coverPhotoId("cover")
			.build();
		travel.setId("travel-1");
		TravelDay travelDay = TravelDay.builder()
			.travelId("travel-1")
			.date(LocalDate.of(2026, 8, 1))
			.sortOrder(1)
			.build();
		travelDay.setId("day-1");
		TravelDayJpo travelDayJpo = new TravelDayJpo();
		TravelPhotoJpo travelLevelPhoto = new TravelPhotoJpo();
		travelLevelPhoto.setId("photo-travel");
		TravelPhotoJpo dayPhoto = new TravelPhotoJpo();
		dayPhoto.setId("photo-day");
		TravelJpo travelJpo = new TravelJpo();

		when(travelDayRepository.findAllByTravelIdOrderBySortOrderAsc("travel-1")).thenReturn(List.of(travelDayJpo));
		when(travelJpoMapper.toDomain(travelDayJpo)).thenReturn(travelDay);
		when(travelPhotoRepository.findAllByTravelIdOrderByTravelDayIdAscTravelPlaceIdAscSortOrderAsc("travel-1"))
			.thenReturn(List.of(travelLevelPhoto, dayPhoto));
		when(travelPlaceRepository.findAllByTravelDayIdIn(List.of("day-1"))).thenReturn(List.of());
		when(travelTagRepository.findAllByTravelIdOrderBySortOrderAscRegisteredTimeAsc("travel-1")).thenReturn(List.of());
		when(travelReviewRepository.findByTravelId("travel-1")).thenReturn(Optional.empty());
		when(travelJpoMapper.toJpo(travelDay)).thenReturn(travelDayJpo);
		when(travelJpoMapper.toJpo(travel)).thenReturn(travelJpo);

		travelStore.deleteTravel(travel);

		verify(travelPhotoRepository).deleteAll(List.of(travelLevelPhoto, dayPhoto));
		verify(travelPhotoRepository, never()).findAllByTravelDayIdIn(any());
		verify(travelRepository).delete(travelJpo);
	}
}
