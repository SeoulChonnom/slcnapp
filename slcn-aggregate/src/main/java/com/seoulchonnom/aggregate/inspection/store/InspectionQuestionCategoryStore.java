package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryConflictException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionCategoryJpo;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionQuestionCategoryJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionQuestionCategoryRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;

import lombok.RequiredArgsConstructor;

/**
 * 이름 유니크 위반과 낙관적 잠금 충돌을 여기서 각각 409로 바꾼다.
 * 이름 중복은 Logic의 앱 레벨 검사를 통과한 두 요청이 동시에 들어오는 경우의 백스톱이고(지역
 * Store와 같은 방식), 버전 충돌 변환은 질문 Store(InspectionQuestionStore.save)와 같은 방식이다
 * (계획 §3 CONFLICT). flush를 당겨야 두 예외가 이 메서드 안에서 잡힌다.
 */
@Repository
@RequiredArgsConstructor
public class InspectionQuestionCategoryStore {
	private final InspectionQuestionCategoryRepository inspectionQuestionCategoryRepository;
	private final InspectionQuestionCategoryJpoMapper inspectionQuestionCategoryJpoMapper;

	public InspectionQuestionCategory save(InspectionQuestionCategory category) {
		try {
			return inspectionQuestionCategoryJpoMapper.toDomain(
				inspectionQuestionCategoryRepository.saveAndFlush(
					inspectionQuestionCategoryJpoMapper.toJpo(category)));
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new InspectionQuestionCategoryConflictException();
		} catch (DataIntegrityViolationException e) {
			throw new InspectionQuestionCategoryDuplicatedException();
		}
	}

	public List<InspectionQuestionCategory> saveAll(List<InspectionQuestionCategory> categories) {
		try {
			return inspectionQuestionCategoryRepository.saveAllAndFlush(categories.stream()
					.map(inspectionQuestionCategoryJpoMapper::toJpo)
					.toList()).stream()
				.map(inspectionQuestionCategoryJpoMapper::toDomain)
				.toList();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new InspectionQuestionCategoryConflictException();
		}
	}

	public InspectionQuestionCategory findById(String categoryId) {
		return inspectionQuestionCategoryRepository.findById(categoryId)
			.map(inspectionQuestionCategoryJpoMapper::toDomain)
			.orElseThrow(InspectionQuestionCategoryNotFoundException::new);
	}

	public Optional<InspectionQuestionCategory> findOptionalByName(String name) {
		return inspectionQuestionCategoryRepository.findByName(name)
			.map(inspectionQuestionCategoryJpoMapper::toDomain);
	}

	public List<InspectionQuestionCategory> findAll() {
		return toDomains(inspectionQuestionCategoryRepository.findAllByOrderBySortOrderAscIdAsc());
	}

	public List<InspectionQuestionCategory> findAllEnabled() {
		return toDomains(inspectionQuestionCategoryRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc());
	}

	public List<InspectionQuestionCategory> findAllByIds(Collection<String> categoryIds) {
		if (categoryIds == null || categoryIds.isEmpty()) {
			return List.of();
		}
		return toDomains(inspectionQuestionCategoryRepository.findAllByIdIn(categoryIds));
	}

	/**
	 * 질문 목록의 분류를 한 번에 붙일 때 쓴다. 정렬과 Rdo 조립이 categoryId로 곧장 찾는다.
	 */
	public Map<String, InspectionQuestionCategory> findMapByIds(Collection<String> categoryIds) {
		return findAllByIds(categoryIds).stream()
			.collect(Collectors.toMap(InspectionQuestionCategory::getId, Function.identity()));
	}

	/**
	 * 등록 채번용. 분류가 하나도 없으면 0이다.
	 */
	public int findMaxSortOrder() {
		return inspectionQuestionCategoryRepository.findMaxSortOrder();
	}

	private List<InspectionQuestionCategory> toDomains(List<InspectionQuestionCategoryJpo> jpos) {
		return jpos.stream().map(inspectionQuestionCategoryJpoMapper::toDomain).toList();
	}
}
