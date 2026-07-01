package com.seoulchonnom.aggregate.travel.store;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.travel.exception.TravelNotFoundException;
import com.seoulchonnom.aggregate.travel.store.mapper.TravelJpoMapper;
import com.seoulchonnom.aggregate.travel.store.repository.TravelRepository;
import com.seoulchonnom.spec.travel.entity.Travel;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TravelStore {
	private final TravelRepository travelRepository;
	private final TravelJpoMapper travelJpoMapper;

	public Travel save(Travel travel) {
		return travelJpoMapper.toDomain(travelRepository.save(travelJpoMapper.toJpo(travel)));
	}

	public List<Travel> findAllVisible() {
		return travelRepository.findAllByHiddenFalseOrderByStartDateDesc()
			.stream()
			.map(travelJpoMapper::toDomain)
			.toList();
	}

	public Travel findById(String travelId) {
		return travelJpoMapper.toDomain(travelRepository.findById(travelId).orElseThrow(TravelNotFoundException::new));
	}

	public void deleteTravel(Travel travel) {
		travelRepository.delete(travelJpoMapper.toJpo(travel));
	}
}
