package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexJpo;
import com.seoulchonnom.aggregate.house.store.mapper.HouseComplexJpoMapper;
import com.seoulchonnom.aggregate.house.store.projection.ComplexMapPdo;
import com.seoulchonnom.aggregate.house.store.projection.DistrictCountPdo;
import com.seoulchonnom.aggregate.house.store.projection.DongSummaryPdo;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexRepository;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

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

	public Optional<HouseComplex> findById(String complexId) {
		return houseComplexRepository.findById(complexId).map(houseComplexJpoMapper::toDomain);
	}

	/**
	 * 좌표 대상. 최근 12개월 거래가 많은 단지부터, 최대 limit개.
	 */
	public List<HouseComplex> findGeocodeTargets(LocalDate today, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return houseComplexRepository.findGeocodeTargets(today.minusMonths(12), limit).stream()
			.map(houseComplexJpoMapper::toDomain)
			.toList();
	}

	/**
	 * 건축물대장 대상. PENDING(거래 많은 순)을 먼저, 남는 자리에 30일 이상 지난 DONE(오래된 순)을 채운다.
	 */
	public List<HouseComplex> findRegisterTargets(LocalDate today, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<HouseComplexJpo> targets = new ArrayList<>(
			houseComplexRepository.findRegisterPendingTargets(today.minusMonths(12), limit));
		int rest = limit - targets.size();
		if (rest > 0) {
			targets.addAll(houseComplexRepository.findRegisterRenewalTargets(today.minusDays(30), rest));
		}
		return targets.stream().map(houseComplexJpoMapper::toDomain).toList();
	}

	/**
	 * 보강 실패 단지 한 페이지. 최근 12개월 거래 건수 내림차순, 그다음 id.
	 *
	 * @param offset 0부터
	 */
	public List<IncompleteComplexPdo> findIncomplete(LocalDate today, int limit, int offset) {
		List<Object[]> rows = houseComplexRepository.findIncompleteIdsWithTrades(today.minusMonths(12), limit, offset);
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<String, HouseComplex> byId = new HashMap<>();
		houseComplexRepository.findAllById(rows.stream().map(row -> (String)row[0]).toList()).stream()
			.map(houseComplexJpoMapper::toDomain)
			.forEach(complex -> byId.put(complex.getId(), complex));
		List<IncompleteComplexPdo> result = new ArrayList<>();
		for (Object[] row : rows) {
			HouseComplex complex = byId.get((String)row[0]);
			if (complex != null) {
				result.add(new IncompleteComplexPdo(complex, ((Number)row[1]).longValue()));
			}
		}
		return result;
	}

	public long countIncomplete() {
		return houseComplexRepository.countIncomplete();
	}

	public long countTrades12m(String complexId, LocalDate today) {
		return houseComplexRepository.countRecentTrades(complexId, today.minusMonths(12));
	}

	/**
	 * 지도 영역·구 단위 목록(최대 limit건). 좌표가 있는 단지만, 최근 실거래가 내림차순.
	 */
	public List<ComplexMapPdo> findMapItems(AreaBucket area, ComplexMapFilter filter, int limit) {
		return houseComplexRepository.findMapItems(area.name(), filter.bounded(), filter.swLat(), filter.swLng(),
			filter.neLat(), filter.neLng(), filter.byDistrict(), filter.lawdCd(), filter.hasPrice(),
			filter.minPrice(), filter.maxPrice(), limit).stream()
			.map(row -> new ComplexMapPdo((String)row[0], (String)row[1], (String)row[2], (String)row[3],
				(Double)row[4], (Double)row[5], toInteger(row[6]), toInteger(row[7]), toLong(row[8]),
				toInteger(row[9]), toInteger(row[10])))
			.toList();
	}

	public long countMapItems(AreaBucket area, ComplexMapFilter filter) {
		return houseComplexRepository.countMapItems(area.name(), filter.bounded(), filter.swLat(), filter.swLng(),
			filter.neLat(), filter.neLng(), filter.byDistrict(), filter.lawdCd(), filter.hasPrice(),
			filter.minPrice(), filter.maxPrice());
	}

	public List<DistrictCountPdo> countByDistrict(AreaBucket area, Long minPrice, Long maxPrice) {
		return houseComplexRepository
			.countByDistrict(area.name(), minPrice != null || maxPrice != null, minPrice == null ? 0 : minPrice,
				maxPrice == null ? Long.MAX_VALUE : maxPrice)
			.stream()
			.map(row -> new DistrictCountPdo((String)row[0], ((Number)row[1]).longValue()))
			.toList();
	}

	public List<DongSummaryPdo> summarizeByDong(AreaBucket area, Long minPrice, Long maxPrice, double swLat,
		double swLng, double neLat, double neLng) {
		return houseComplexRepository
			.summarizeByDong(area.name(), minPrice != null || maxPrice != null, minPrice == null ? 0 : minPrice,
				maxPrice == null ? Long.MAX_VALUE : maxPrice, swLat, swLng, neLat, neLng)
			.stream()
			.map(row -> new DongSummaryPdo((String)row[0], (String)row[1], (String)row[2],
				((Number)row[3]).doubleValue(), ((Number)row[4]).doubleValue(), ((Number)row[5]).longValue()))
			.toList();
	}

	private static Integer toInteger(Object value) {
		return value == null ? null : ((Number)value).intValue();
	}

	private static Long toLong(Object value) {
		return value == null ? null : ((Number)value).longValue();
	}
}
