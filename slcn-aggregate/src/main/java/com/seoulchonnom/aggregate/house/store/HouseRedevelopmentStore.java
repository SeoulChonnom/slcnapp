package com.seoulchonnom.aggregate.house.store;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.stat.RedevelopmentLinkCalculator.Link;
import com.seoulchonnom.aggregate.house.store.mapper.HouseRedevelopmentJpoMapper;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexRedevelopmentRepository;
import com.seoulchonnom.aggregate.house.store.repository.HouseRedevelopmentRepository;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

import lombok.RequiredArgsConstructor;

/**
 * 정비사업 구역과 단지 연결. 교체·연결 재생성은 호출하는 쪽(HouseRedevelopmentLogic)의 트랜잭션 안에서 한다.
 */
@Repository
@RequiredArgsConstructor
public class HouseRedevelopmentStore {
	/** 한 문장에 싣는 연결 행 수. 문자열 셋이 너무 커지지 않게 나눈다. */
	private static final int INSERT_CHUNK = 2000;

	private final HouseRedevelopmentRepository houseRedevelopmentRepository;
	private final HouseComplexRedevelopmentRepository houseComplexRedevelopmentRepository;
	private final HouseRedevelopmentJpoMapper houseRedevelopmentJpoMapper;

	public List<HouseRedevelopment> findAll() {
		return houseRedevelopmentRepository.findAll().stream().map(houseRedevelopmentJpoMapper::toDomain).toList();
	}

	public long count() {
		return houseRedevelopmentRepository.count();
	}

	public HouseRedevelopment save(HouseRedevelopment redevelopment) {
		return houseRedevelopmentJpoMapper.toDomain(
			houseRedevelopmentRepository.save(houseRedevelopmentJpoMapper.toJpo(redevelopment)));
	}

	/**
	 * 구역과 그 구역의 단지 연결을 함께 지운다.
	 */
	public void deleteWithLinks(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		houseComplexRedevelopmentRepository.deleteByRedevelopmentIds(String.join(",", ids));
		houseRedevelopmentRepository.deleteAllById(ids);
	}

	public long countByGeocodeStatus(GeocodeStatus status) {
		return houseRedevelopmentRepository.countByGeocodeStatus(status);
	}

	/**
	 * 좌표를 기다리는 구역. 자치구, 코드 순이다.
	 */
	public List<HouseRedevelopment> findPending() {
		return houseRedevelopmentRepository.findByGeocodeStatusOrderByLawdCdAscSourceKeyAsc(GeocodeStatus.PENDING)
			.stream().map(houseRedevelopmentJpoMapper::toDomain).toList();
	}

	/**
	 * 좌표가 확정된 구역(DONE이고 좌표가 있는 것).
	 */
	public List<HouseRedevelopment> findGeocoded() {
		return houseRedevelopmentRepository.findByGeocodeStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(
			GeocodeStatus.DONE).stream().map(houseRedevelopmentJpoMapper::toDomain).toList();
	}

	/**
	 * 좌표 PENDING/FAILED 구역. 자치구, 구역명 순.
	 *
	 * @param page 0부터
	 */
	public List<HouseRedevelopment> findIncomplete(int page, int size) {
		return houseRedevelopmentRepository.findByGeocodeStatusInOrderByLawdCdAscNameAscSourceKeyAsc(
			INCOMPLETE, PageRequest.of(page, size)).stream().map(houseRedevelopmentJpoMapper::toDomain).toList();
	}

	public long countIncomplete() {
		return houseRedevelopmentRepository.countByGeocodeStatusIn(INCOMPLETE);
	}

	private static final List<GeocodeStatus> INCOMPLETE = List.of(GeocodeStatus.PENDING, GeocodeStatus.FAILED);

	/**
	 * 단지-정비사업 연결을 통째로 바꾼다(전부 지우고 다시 넣는다).
	 *
	 * @return 넣은 행 수
	 */
	public int replaceLinks(List<Link> links) {
		houseComplexRedevelopmentRepository.deleteAllRows();
		int inserted = 0;
		for (int from = 0; from < links.size(); from += INSERT_CHUNK) {
			List<Link> chunk = links.subList(from, Math.min(from + INSERT_CHUNK, links.size()));
			inserted += houseComplexRedevelopmentRepository.insertAll(join(chunk, Link::complexId),
				join(chunk, Link::redevelopmentId), join(chunk, link -> String.valueOf(link.distanceM())));
		}
		return inserted;
	}

	private static <T> String join(List<T> rows, Function<T, String> field) {
		return String.join(",", rows.stream().map(field).toList());
	}
}
