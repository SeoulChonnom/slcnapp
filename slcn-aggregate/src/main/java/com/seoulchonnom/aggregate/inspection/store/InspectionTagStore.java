package com.seoulchonnom.aggregate.inspection.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionTagJpo;
import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitTagJpo;
import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyTagJpo;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionTagJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionTagRepository;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionVisitTagRepository;
import com.seoulchonnom.aggregate.inspection.store.repository.ViewedPropertyTagRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionTagScope;

import lombok.RequiredArgsConstructor;

/**
 * 태그 마스터와 두 연결 테이블을 함께 다룬다. 임장 태그와 매물 태그가 같은 마스터를 공유하므로
 * 마스터 조회·생성 규칙이 한 곳에 있어야 한다.
 */
@Repository
@RequiredArgsConstructor
public class InspectionTagStore {
	private final InspectionTagRepository inspectionTagRepository;
	private final InspectionVisitTagRepository inspectionVisitTagRepository;
	private final ViewedPropertyTagRepository viewedPropertyTagRepository;
	private final InspectionTagJpoMapper inspectionTagJpoMapper;

	/**
	 * 이름으로 찾고 없으면 만든다.
	 *
	 * 동시에 같은 신규 태그를 쓰면 나중 요청이 유니크 위반에 걸리는데, 이는 사용자 오류가 아니라
	 * 정상 흐름이다. 에러로 올리지 않고 재조회해 기존 행을 재사용한다.
	 */
	public List<InspectionTag> getOrCreateAll(List<String> names) {
		if (names == null || names.isEmpty()) {
			return List.of();
		}
		Map<String, InspectionTag> found = inspectionTagRepository.findAllByNameIn(names).stream()
			.map(inspectionTagJpoMapper::toDomain)
			.collect(Collectors.toMap(InspectionTag::getName, Function.identity(), (a, b) -> a));

		Map<String, InspectionTag> resolved = new LinkedHashMap<>();
		for (String name : names) {
			resolved.computeIfAbsent(name, key -> {
				InspectionTag existing = found.get(key);
				return existing != null ? existing : create(key);
			});
		}
		return new ArrayList<>(resolved.values());
	}

	private InspectionTag create(String name) {
		try {
			return inspectionTagJpoMapper.toDomain(
				inspectionTagRepository.saveAndFlush(new InspectionTagJpo(name)));
		} catch (DataIntegrityViolationException e) {
			return inspectionTagRepository.findByName(name)
				.map(inspectionTagJpoMapper::toDomain)
				.orElseThrow(() -> e);
		}
	}

	public List<InspectionTag> findAllByKeyword(String keyword) {
		List<InspectionTagJpo> jpos = keyword == null || keyword.isBlank()
			? inspectionTagRepository.findAll()
			: inspectionTagRepository.findAllByNameStartingWithOrderByNameAsc(keyword.trim());
		return jpos.stream().map(inspectionTagJpoMapper::toDomain).toList();
	}

	/**
	 * 자동완성 정렬 기준인 사용 빈도.
	 *
	 * scope가 없으면(하위호환) 임장 연결과 매물 연결을 합쳐 센다. scope가 있으면 해당 연결
	 * 리포지토리 하나만 조회한다 — 임장 태그와 매물 태그는 용도가 다른데(B-⑨) 지금까지는 합산만
	 * 지원해 매물 입력창에 임장 전용 태그가 1순위로 뜨는 문제가 있었다. 둘 다 조회하지 않으므로
	 * scope 지정 시 오히려 저장소 왕복이 하나 줄어든다.
	 */
	public Map<String, Integer> countUsageByTagIds(Collection<String> tagIds, InspectionTagScope scope) {
		if (tagIds == null || tagIds.isEmpty()) {
			return Map.of();
		}
		Map<String, Integer> usage = new HashMap<>();
		if (scope != InspectionTagScope.PROPERTY) {
			inspectionVisitTagRepository.findAllByTagIdIn(tagIds)
				.forEach(link -> usage.merge(link.getTagId(), 1, Integer::sum));
		}
		if (scope != InspectionTagScope.VISIT) {
			viewedPropertyTagRepository.findAllByTagIdIn(tagIds)
				.forEach(link -> usage.merge(link.getTagId(), 1, Integer::sum));
		}
		return usage;
	}

	public Map<String, InspectionTag> findMapByIds(Collection<String> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) {
			return Map.of();
		}
		return inspectionTagRepository.findAllByIdIn(tagIds).stream()
			.map(inspectionTagJpoMapper::toDomain)
			.collect(Collectors.toMap(InspectionTag::getId, Function.identity()));
	}

	public void linkVisit(String inspectionVisitId, List<InspectionTag> tags) {
		inspectionVisitTagRepository.deleteByInspectionVisitId(inspectionVisitId);
		inspectionVisitTagRepository.flush();
		if (tags.isEmpty()) {
			return;
		}
		inspectionVisitTagRepository.saveAll(tags.stream()
			.map(tag -> newVisitLink(inspectionVisitId, tag.getId()))
			.toList());
	}

	public void linkProperty(String viewedPropertyId, String inspectionVisitId, List<InspectionTag> tags) {
		viewedPropertyTagRepository.deleteByViewedPropertyId(viewedPropertyId);
		viewedPropertyTagRepository.flush();
		if (tags.isEmpty()) {
			return;
		}
		viewedPropertyTagRepository.saveAll(tags.stream()
			.map(tag -> newPropertyLink(viewedPropertyId, inspectionVisitId, tag.getId()))
			.toList());
	}

	private InspectionVisitTagJpo newVisitLink(String inspectionVisitId, String tagId) {
		InspectionVisitTagJpo jpo = new InspectionVisitTagJpo(inspectionVisitId, tagId);
		jpo.setId(UUID.randomUUID().toString());
		return jpo;
	}

	private ViewedPropertyTagJpo newPropertyLink(String viewedPropertyId, String inspectionVisitId, String tagId) {
		ViewedPropertyTagJpo jpo = new ViewedPropertyTagJpo(viewedPropertyId, inspectionVisitId, tagId);
		jpo.setId(UUID.randomUUID().toString());
		return jpo;
	}

	public List<String> findVisitTagNames(String inspectionVisitId) {
		return namesOf(inspectionVisitTagRepository.findAllByInspectionVisitId(inspectionVisitId).stream()
			.map(InspectionVisitTagJpo::getTagId)
			.toList());
	}

	/**
	 * 임장 목록이 행마다 태그를 끌어오지 않도록 한 번에 읽는다.
	 */
	public Map<String, List<String>> findVisitTagNamesByVisitIds(Collection<String> inspectionVisitIds) {
		if (inspectionVisitIds == null || inspectionVisitIds.isEmpty()) {
			return Map.of();
		}
		List<InspectionVisitTagJpo> links = inspectionVisitTagRepository
			.findAllByInspectionVisitIdIn(inspectionVisitIds);
		Map<String, InspectionTag> tags = findMapByIds(links.stream()
			.map(InspectionVisitTagJpo::getTagId)
			.collect(Collectors.toSet()));

		Map<String, List<String>> grouped = new HashMap<>();
		for (InspectionVisitTagJpo link : links) {
			InspectionTag tag = tags.get(link.getTagId());
			if (tag != null) {
				grouped.computeIfAbsent(link.getInspectionVisitId(), key -> new ArrayList<>()).add(tag.getName());
			}
		}
		grouped.values().forEach(Collections::sort);
		return grouped;
	}

	public Map<String, List<String>> findPropertyTagNamesByVisitId(String inspectionVisitId) {
		List<ViewedPropertyTagJpo> links = viewedPropertyTagRepository.findAllByInspectionVisitId(inspectionVisitId);
		return groupPropertyTags(links);
	}

	public Map<String, List<String>> findPropertyTagNamesByPropertyIds(Collection<String> viewedPropertyIds) {
		if (viewedPropertyIds == null || viewedPropertyIds.isEmpty()) {
			return Map.of();
		}
		return groupPropertyTags(viewedPropertyTagRepository.findAllByViewedPropertyIdIn(viewedPropertyIds));
	}

	public void deleteVisitLinks(String inspectionVisitId) {
		inspectionVisitTagRepository.deleteByInspectionVisitId(inspectionVisitId);
	}

	public void deletePropertyLinksByVisitId(String inspectionVisitId) {
		viewedPropertyTagRepository.deleteByInspectionVisitId(inspectionVisitId);
	}

	public void deletePropertyLinks(String viewedPropertyId) {
		viewedPropertyTagRepository.deleteByViewedPropertyId(viewedPropertyId);
	}

	private Map<String, List<String>> groupPropertyTags(List<ViewedPropertyTagJpo> links) {
		Map<String, InspectionTag> tags = findMapByIds(links.stream()
			.map(ViewedPropertyTagJpo::getTagId)
			.collect(Collectors.toSet()));

		Map<String, List<String>> grouped = new HashMap<>();
		for (ViewedPropertyTagJpo link : links) {
			InspectionTag tag = tags.get(link.getTagId());
			if (tag != null) {
				grouped.computeIfAbsent(link.getViewedPropertyId(), key -> new ArrayList<>()).add(tag.getName());
			}
		}
		grouped.values().forEach(Collections::sort);
		return grouped;
	}

	private List<String> namesOf(Collection<String> tagIds) {
		Set<String> ids = Set.copyOf(tagIds);
		return findMapByIds(ids).values().stream()
			.map(InspectionTag::getName)
			.sorted()
			.toList();
	}
}
