package com.seoulchonnom.aggregate.inspection.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionTagMapper;

import lombok.RequiredArgsConstructor;

/**
 * 임장 태그와 매물 태그가 같은 마스터 풀을 공유한다. 용도 구분 필드를 두지 않는다.
 * 생성/삭제 API는 없고 임장·매물 저장 시 이름 기반 get-or-create로만 만들어진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionTagLogic {
	static final int MAX_TAG_COUNT = 10;

	private final InspectionTagStore inspectionTagStore;
	private final InspectionTagMapper inspectionTagMapper;

	/**
	 * 자동완성. 사용 빈도 내림차순, 동률이면 이름순.
	 */
	public List<InspectionTagRdo> getInspectionTags(String keyword) {
		List<InspectionTag> tags = inspectionTagStore.findAllByKeyword(keyword);
		Map<String, Integer> usage = inspectionTagStore.countUsageByTagIds(tags.stream()
			.map(InspectionTag::getId)
			.collect(Collectors.toSet()));

		return tags.stream()
			.map(tag -> inspectionTagMapper.toInspectionTagRdo(tag, usage.getOrDefault(tag.getId(), 0)))
			.sorted(Comparator.comparingInt(InspectionTagRdo::getUsageCount).reversed()
				.thenComparing(InspectionTagRdo::getName))
			.toList();
	}

	/**
	 * 요청의 이름 목록을 정규화해 마스터 행으로 바꾼다.
	 *
	 * @param rawNames null이면 "수정하지 않음"이라 호출자가 먼저 걸러야 한다. 빈 목록은 "전부 해제"다
	 */
	@Transactional
	public List<InspectionTag> resolveTags(List<String> rawNames) {
		if (rawNames == null || rawNames.isEmpty()) {
			return List.of();
		}
		Set<String> normalized = new LinkedHashSet<>();
		for (String rawName : rawNames) {
			String name = inspectionTagMapper.normalizeName(rawName);
			if (name != null) {
				normalized.add(name);
			}
		}
		if (normalized.size() > MAX_TAG_COUNT) {
			throw new BadRequestException("태그는 최대 " + MAX_TAG_COUNT + "개까지 등록할 수 있습니다.");
		}
		if (normalized.isEmpty()) {
			return List.of();
		}
		return inspectionTagStore.getOrCreateAll(new ArrayList<>(normalized));
	}
}
