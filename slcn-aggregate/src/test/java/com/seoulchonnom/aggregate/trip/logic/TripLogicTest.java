package com.seoulchonnom.aggregate.trip.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.mapper.TripMapper;

class TripLogicTest {
	private final TripStore tripStore = mock(TripStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final TripMapper tripMapper = mock(TripMapper.class);
	private final TripLogic tripLogic = new TripLogic(tripStore, idGenerator, tripMapper);

	@Test
	void registerTrip_shouldGenerateTripIdAndPersistQuizStructure() {
		TripCdo tripCdo = createValidTripCdo();
		TripDetailRdo tripDetailRdo = new TripDetailRdo();
		when(idGenerator.nextDomainId("TRIP")).thenReturn("TRIP-0001");
		when(tripMapper.toTripDetailRdo(any(Trip.class))).thenReturn(tripDetailRdo);

		tripLogic.registerTrip(tripCdo);

		ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
		verify(tripStore).saveTrip(tripCaptor.capture());
		assertThat(tripCaptor.getValue().getId()).isEqualTo("TRIP-0001");
		assertThat(tripCaptor.getValue().getQuiz()).isEqualTo("TRIP-0001");
		assertThat(tripCaptor.getValue().getQuiz().getCorrectOptionId()).isNotBlank();
		assertThat(tripCaptor.getValue().getQuiz().getOptions()).hasSize(2);
	}

	@Test
	void registerTrip_shouldRejectWhenCorrectOptionIsMissing() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.getQuiz().getOptions().forEach(option -> option.setCorrect(false));

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldRejectWhenSortOrderIsDuplicated() {
		TripCdo tripCdo = createValidTripCdo();

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	@Test
	void registerTrip_shouldRejectWhenNavigationFieldsArePartial() {
		TripCdo tripCdo = createValidTripCdo();
		tripCdo.setSecondMap("second-map.png");
		tripCdo.setNextButtonText("next");
		tripCdo.setPreviousButtonText(null);

		assertThatThrownBy(() -> tripLogic.registerTrip(tripCdo))
			.isInstanceOf(InvalidTripRegisterException.class);
	}

	private TripCdo createValidTripCdo() {
		return new TripCdo(
			"2026-04-16",
			"ryu",
			"봄 나들이",
			"/logo.png",
			"/first-map.png",
			null,
			null,
			null,
			"https://drive.example",
			new QuizCdo(
				"퀴즈 제목",
				"정답 제목",
				"정답 설명",
					"오답 제목",
					"오답 설명",
					List.of(
						new OptionCdo("오답", false),
						new OptionCdo("정답", true))));
	}
}
