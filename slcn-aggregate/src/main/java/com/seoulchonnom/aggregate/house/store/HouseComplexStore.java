package com.seoulchonnom.aggregate.house.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.mapper.HouseComplexJpoMapper;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexRepository;
import com.seoulchonnom.spec.house.entity.HouseComplex;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class HouseComplexStore {
	private final HouseComplexRepository houseComplexRepository;
	private final HouseComplexJpoMapper houseComplexJpoMapper;

	/**
	 * aptSeq로 단지를 모아 찾는다. 없는 aptSeq는 결과에 없다.
	 */
	public Map<String, HouseComplex> findByAptSeqIn(Collection<String> aptSeqs) {
		Map<String, HouseComplex> found = new HashMap<>();
		if (aptSeqs == null || aptSeqs.isEmpty()) {
			return found;
		}
		houseComplexRepository.findByAptSeqIn(aptSeqs).stream()
			.map(houseComplexJpoMapper::toDomain)
			.forEach(complex -> found.put(complex.getAptSeq(), complex));
		return found;
	}

	public HouseComplex save(HouseComplex complex) {
		return houseComplexJpoMapper.toDomain(houseComplexRepository.save(houseComplexJpoMapper.toJpo(complex)));
	}
}
