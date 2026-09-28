package com.seoulchonnom.aggregate.inspection.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryInUseException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionCategoryException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryUdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionCategoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 질문 대분류의 등록/이름 변경/상태/정렬을 관리한다.
 *
 * Logic끼리는 서로 호출하지 않는다 — 비활성화 시 활성 질문 수 확인은 InspectionQuestionLogic이
 * 아니라 InspectionQuestionStore를 직접 참조한다(계획 §4 aggregate-5).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionQuestionCategoryLogic {
	private static final int MAX_NAME_LENGTH = 50;

	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore;
	private final InspectionQuestionStore inspectionQuestionStore;
	private final InspectionQuestionCategoryMapper inspectionQuestionCategoryMapper;
	private final IdGenerator idGenerator;

	public List<InspectionQuestionCategoryRdo> getInspectionQuestionCategories(boolean includeDisabled) {
		List<InspectionQuestionCategory> categories = includeDisabled
			? inspectionQuestionCategoryStore.findAll()
			: inspectionQuestionCategoryStore.findAllEnabled();
		Map<String, Integer> enabledCounts = inspectionQuestionStore.countEnabledGroupByCategoryId();
		return categories.stream()
			.map(category -> inspectionQuestionCategoryMapper.toInspectionQuestionCategoryRdo(category,
				enabledCounts.getOrDefault(category.getId(), 0)))
			.toList();
	}

	/**
	 * 비활성 분류까지 포함해 전체에서 이름 중복을 금지한다(계획 §0-6).
	 * sortOrder가 0 이하면 맨 뒤(max+1)로 채번한다.
	 */
	@Transactional
	public InspectionQuestionCategoryRdo registerInspectionQuestionCategory(
		InspectionQuestionCategoryCdo inspectionQuestionCategoryCdo) {
		String name = normalizeName(inspectionQuestionCategoryCdo.getName());
		rejectDuplicateName(name, null);

		int sortOrder = inspectionQuestionCategoryCdo.getSortOrder();
		if (sortOrder <= 0) {
			sortOrder = inspectionQuestionCategoryStore.findMaxSortOrder() + 1;
		}

		String categoryId = idGenerator.nextDomainId(SequenceName.INSPECTION_QUESTION_CATEGORY.toString());
		InspectionQuestionCategory category = new InspectionQuestionCategory(categoryId, name, sortOrder);
		InspectionQuestionCategory saved = inspectionQuestionCategoryStore.save(category);
		// 방금 만든 분류라 소속 질문이 있을 수 없다 — 조회 없이 0으로 바로 응답한다.
		return inspectionQuestionCategoryMapper.toInspectionQuestionCategoryRdo(saved, 0);
	}

	/**
	 * 자기 자신과의 이름 충돌은 허용한다(변경 없는 저장). 나머지는 등록과 같은 검증이다.
	 */
	@Transactional
	public InspectionQuestionCategoryRdo renameInspectionQuestionCategory(String categoryId,
		InspectionQuestionCategoryUdo inspectionQuestionCategoryUdo) {
		InspectionQuestionCategory category = inspectionQuestionCategoryStore.findById(categoryId);
		String name = normalizeName(inspectionQuestionCategoryUdo.getName());
		rejectDuplicateName(name, categoryId);

		category.rename(name);
		InspectionQuestionCategory saved = inspectionQuestionCategoryStore.save(category);
		return toRdoWithEnabledCount(saved);
	}

	/**
	 * 비활성화는 활성 질문이 0개일 때만 허용한다(계획 §0-3, A안).
	 */
	@Transactional
	public InspectionQuestionCategoryRdo changeInspectionQuestionCategoryStatus(String categoryId,
		InspectionQuestionCategoryStatusUdo inspectionQuestionCategoryStatusUdo) {
		InspectionQuestionCategory category = inspectionQuestionCategoryStore.findById(categoryId);
		if (!inspectionQuestionCategoryStatusUdo.isEnabled()
			&& inspectionQuestionStore.countEnabledByCategoryId(categoryId) > 0) {
			throw new InspectionQuestionCategoryInUseException();
		}

		category.changeEnabled(inspectionQuestionCategoryStatusUdo.isEnabled());
		InspectionQuestionCategory saved = inspectionQuestionCategoryStore.save(category);
		return toRdoWithEnabledCount(saved);
	}

	/**
	 * 요청에 빠진 분류는 기존 순서를 유지한다. 존재하지 않는 분류가 섞이면 통째로 막는다
	 * (질문 정렬과 같은 규칙).
	 */
	@Transactional
	public void modifyInspectionQuestionCategoryOrder(List<InspectionQuestionCategoryOrderUdo> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		Map<String, Integer> requested = new HashMap<>();
		for (InspectionQuestionCategoryOrderUdo order : orders) {
			if (!StringUtils.hasText(order.getCategoryId())) {
				throw new InvalidInspectionQuestionCategoryException("정렬 대상 분류 ID가 비어 있습니다.");
			}
			requested.put(order.getCategoryId(), order.getSortOrder());
		}

		List<InspectionQuestionCategory> categories = inspectionQuestionCategoryStore.findAllByIds(
			requested.keySet());
		if (categories.size() != requested.size()) {
			throw new InvalidInspectionQuestionCategoryException("존재하지 않는 분류가 정렬 요청에 포함되었습니다.");
		}
		categories.forEach(category -> category.changeSortOrder(requested.get(category.getId())));
		inspectionQuestionCategoryStore.saveAll(categories);
	}

	private InspectionQuestionCategoryRdo toRdoWithEnabledCount(InspectionQuestionCategory category) {
		long enabledQuestionCount = inspectionQuestionStore.countEnabledByCategoryId(category.getId());
		return inspectionQuestionCategoryMapper.toInspectionQuestionCategoryRdo(category, (int)enabledQuestionCount);
	}

	private String normalizeName(String rawName) {
		if (!StringUtils.hasText(rawName)) {
			throw new InvalidInspectionQuestionCategoryException("분류명은 필수입니다.");
		}
		String name = rawName.trim();
		if (name.length() > MAX_NAME_LENGTH) {
			throw new InvalidInspectionQuestionCategoryException("분류명이 너무 깁니다.");
		}
		return name;
	}

	/**
	 * 비활성 분류까지 포함해 검사한다(계획 §0-6) — Store.findOptionalByName은 enabled를 가리지 않는다.
	 */
	private void rejectDuplicateName(String name, String excludingCategoryId) {
		Optional<InspectionQuestionCategory> existing = inspectionQuestionCategoryStore.findOptionalByName(name);
		if (existing.isPresent() && !existing.get().getId().equals(excludingCategoryId)) {
			throw new InspectionQuestionCategoryDuplicatedException(
				"같은 이름의 질문 분류가 이미 있습니다. categoryId=" + existing.get().getId());
		}
	}
}
