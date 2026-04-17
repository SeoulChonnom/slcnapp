package com.seoulchonnom.aggregate.trip.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.trip.exception.InvalidTripRegisterException;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.TripQuiz;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizOptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizRdo;
import com.seoulchonnom.spec.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogic {
	private final TripStore tripStore;
	private final IdGenerator idGenerator;
	private final TripMapper tripMapper;

	public List<TripListRdo> getAllTripList() {
		return tripStore.findAllByOrderByDateDesc().stream().map(tripMapper::toTripListRdo).toList();
	}

	public TripDetailRdo getTripById(String tripId) {
		return tripMapper.toTripDetailRdo(tripStore.findById(tripId));
	}

	@Transactional
	public TripDetailRdo registerTrip(TripCdo tripCdo) {
		String nextTripId = idGenerator.nextDomainId(SequenceName.TRIP.toString());

		validateTrip(tripCdo);

		Trip trip = new Trip(tripCdo, nextTripId);
		tripStore.saveTrip(trip);
		return tripMapper.toTripDetailRdo(trip);
	}

	public TripQuizRdo getTripQuiz(String tripId) {
		return tripMapper.toTripQuizRdo(tripStore.findById(tripId).getQuiz());
	}

	public TripQuizDetailRdo checkTripQuizAnswer(String tripId, String optionId) {
		TripQuiz tripQuiz = tripStore.findById(tripId).getQuiz();
		return tripMapper.toTripQuizDetailRdo(tripQuiz, optionId);
	}

	private void validateTrip(TripCdo tripCdo) {
		if (tripCdo.getQuiz() == null ||
			tripCdo.getQuiz().getOptions() == null ||
			tripCdo.getQuiz().getOptions().isEmpty()) {
			throw new InvalidTripRegisterException();
		}

		long correctOptionCount = tripCdo.getQuiz().getOptions().stream()
			.filter(TripQuizOptionCdo::isCorrect)
			.count();
		if (correctOptionCount != 1L) {
			throw new InvalidTripRegisterException();
		}

		Set<Integer> sortOrderSet = new HashSet<>();
		for (TripQuizOptionCdo option : tripCdo.getQuiz().getOptions()) {
			if (option.getSortOrder() == null) {
				throw new InvalidTripRegisterException();
			}
			if (!sortOrderSet.add(option.getSortOrder())) {
				throw new InvalidTripRegisterException();
			}
		}

		boolean hasSecondMap = StringUtils.hasText(tripCdo.getSecondMap());
		boolean hasNextButtonText = StringUtils.hasText(tripCdo.getNextButtonText());
		boolean hasPreviousButtonText = StringUtils.hasText(tripCdo.getPreviousButtonText());

		boolean allNavigationFieldsPresent = hasSecondMap && hasNextButtonText && hasPreviousButtonText;
		boolean allNavigationFieldsAbsent = !hasSecondMap && !hasNextButtonText && !hasPreviousButtonText;

		if (!(allNavigationFieldsPresent || allNavigationFieldsAbsent)) {
			throw new InvalidTripRegisterException();
		}
	}
}
