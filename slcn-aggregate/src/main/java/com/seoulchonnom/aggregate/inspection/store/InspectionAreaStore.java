package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionAreaJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionAreaRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionAreaSort;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InspectionAreaStore {
	private final InspectionAreaRepository inspectionAreaRepository;
	private final InspectionAreaJpoMapper inspectionAreaJpoMapper;

	/**
	 * 지역명 유니크 위반을 409로 바꾼다. 앱 레벨 중복 검사를 통과한 두 요청이 동시에
	 * 들어오는 경우가 여기로 온다. flush를 당겨야 예외가 이 메서드 안에서 잡힌다.
	 */
	public InspectionArea save(InspectionArea area) {
		try {
			return inspectionAreaJpoMapper.toDomain(
				inspectionAreaRepository.saveAndFlush(inspectionAreaJpoMapper.toJpo(area)));
		} catch (DataIntegrityViolationException e) {
			throw new InspectionAreaDuplicatedException();
		}
	}

	public InspectionArea findById(String areaId) {
		return inspectionAreaRepository.findById(areaId)
			.map(inspectionAreaJpoMapper::toDomain)
			.orElseThrow(InspectionAreaNotFoundException::new);
	}

	public Optional<InspectionArea> findOptionalByName(String name) {
		return inspectionAreaRepository.findByName(name).map(inspectionAreaJpoMapper::toDomain);
	}

	public List<InspectionArea> findAllVisible(String keyword) {
		return (StringUtils.hasText(keyword)
			? inspectionAreaRepository.findAllByHiddenFalseAndNameContainingOrderByNameAsc(keyword.trim())
			: inspectionAreaRepository.findAllByHiddenFalseOrderByNameAsc()).stream()
			.map(inspectionAreaJpoMapper::toDomain)
			.toList();
	}

	public List<InspectionArea> findAllByIds(Collection<String> areaIds) {
		if (areaIds == null || areaIds.isEmpty()) {
			return List.of();
		}
		return inspectionAreaRepository.findAllByIdIn(areaIds).stream()
			.map(inspectionAreaJpoMapper::toDomain)
			.toList();
	}

	/**
	 * sort별로 별도 네이티브 쿼리를 쓴다(InspectionAreaRepository 참고). 반환값은 area id만
	 * 담은, 이미 정렬·페이징이 끝난 목록이다 — findAllByIds는 이 순서를 보존하지 않으므로
	 * 호출자가 이 id 순서대로 다시 정렬해야 한다.
	 */
	public List<String> findAreaIdsPage(InspectionAreaSort sort, String keyword, RevisitIntent revisitIntent,
		int limit, int offset) {
		String likeKeyword = likeKeywordOf(keyword);
		String revisitIntentName = revisitIntent == null ? null : revisitIntent.name();
		return switch (sort) {
			case VISIT_COUNT -> inspectionAreaRepository.findAreaIdsOrderByVisitCount(likeKeyword, revisitIntentName,
				limit, offset);
			case TOP_INTEREST -> inspectionAreaRepository.findAreaIdsOrderByTopInterest(likeKeyword,
				revisitIntentName, limit, offset);
			case RECENT_VISIT -> inspectionAreaRepository.findAreaIdsOrderByRecentVisit(likeKeyword,
				revisitIntentName, limit, offset);
		};
	}

	/**
	 * 페이지 응답의 totalCount. sort와 무관하게 keyword/revisitIntent 필터만 같으면 같은 값이다.
	 */
	public long countMatchingAreas(String keyword, RevisitIntent revisitIntent) {
		return inspectionAreaRepository.countMatchingAreas(likeKeywordOf(keyword),
			revisitIntent == null ? null : revisitIntent.name());
	}

	/**
	 * 필터 무관 전역 지역 수. revisitIntentCounts.total과 totals.areaCount가 함께 쓴다.
	 */
	public long countVisibleAreas() {
		return inspectionAreaRepository.countByHiddenFalse();
	}

	public long countVisitsOfVisibleAreas() {
		return inspectionAreaRepository.countVisitsOfVisibleAreas();
	}

	public long countPropertiesOfVisibleAreas() {
		return inspectionAreaRepository.countPropertiesOfVisibleAreas();
	}

	/**
	 * 최신 회차의 revisitIntent별 지역 수. intent가 null인 그룹(임장 0건이거나 최신 회차의
	 * revisitIntent 미입력)은 여기서 버린다 — 호출자는 total을 countVisibleAreas()로 따로 구한다.
	 */
	public Map<RevisitIntent, Long> countAreasByLatestRevisitIntent() {
		Map<RevisitIntent, Long> result = new EnumMap<>(RevisitIntent.class);
		for (Object[] row : inspectionAreaRepository.countAreasByLatestRevisitIntent()) {
			if (row[0] == null) {
				continue;
			}
			result.put(RevisitIntent.valueOf((String)row[0]), ((Number)row[1]).longValue());
		}
		return result;
	}

	private String likeKeywordOf(String keyword) {
		return StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : null;
	}

	public void delete(InspectionArea area) {
		inspectionAreaRepository.deleteById(area.getId());
	}
}
