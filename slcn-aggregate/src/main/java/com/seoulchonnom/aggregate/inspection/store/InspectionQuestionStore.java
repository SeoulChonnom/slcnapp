package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionConflictException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionJpo;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionQuestionJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionQuestionRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InspectionQuestionStore {
	private final InspectionQuestionRepository inspectionQuestionRepository;
	private final InspectionQuestionJpoMapper inspectionQuestionJpoMapper;

	/**
	 * 버전 이력이 질문 행 안의 JSON이라 두 관리자가 같은 질문을 동시에 고치면
	 * 낙관적 잠금이 나중 요청을 막는다. 커밋 시점까지 미루면 예외가 트랜잭션 밖에서
	 * 500으로 새어나가므로 여기서 flush해 409로 바꾼다.
	 */
	public InspectionQuestion save(InspectionQuestion question) {
		try {
			return inspectionQuestionJpoMapper.toDomain(
				inspectionQuestionRepository.saveAndFlush(inspectionQuestionJpoMapper.toJpo(question)));
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new InspectionQuestionConflictException();
		}
	}

	public List<InspectionQuestion> saveAll(List<InspectionQuestion> questions) {
		try {
			return inspectionQuestionRepository.saveAllAndFlush(questions.stream()
					.map(inspectionQuestionJpoMapper::toJpo)
					.toList()).stream()
				.map(inspectionQuestionJpoMapper::toDomain)
				.toList();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new InspectionQuestionConflictException();
		}
	}

	public InspectionQuestion findById(String questionId) {
		return inspectionQuestionRepository.findById(questionId)
			.map(inspectionQuestionJpoMapper::toDomain)
			.orElseThrow(InspectionQuestionNotFoundException::new);
	}

	public List<InspectionQuestion> findAll() {
		return toDomains(inspectionQuestionRepository.findAllByOrderBySortOrderAscIdAsc());
	}

	/**
	 * 매물 생성 시 스냅샷 대상이 되는 질문 집합이다.
	 */
	public List<InspectionQuestion> findAllEnabled() {
		return toDomains(inspectionQuestionRepository.findAllByEnabledTrueOrderBySortOrderAscIdAsc());
	}

	public List<InspectionQuestion> findAllByIds(Collection<String> questionIds) {
		if (questionIds == null || questionIds.isEmpty()) {
			return List.of();
		}
		return toDomains(inspectionQuestionRepository.findAllByIdIn(questionIds));
	}

	/**
	 * 배지 계산처럼 questionId로 곧장 찾아야 하는 곳에서 쓴다.
	 */
	public Map<String, InspectionQuestion> findMapByIds(Collection<String> questionIds) {
		return findAllByIds(questionIds).stream()
			.collect(Collectors.toMap(InspectionQuestion::getId, Function.identity()));
	}

	private List<InspectionQuestion> toDomains(List<InspectionQuestionJpo> jpos) {
		return jpos.stream().map(inspectionQuestionJpoMapper::toDomain).toList();
	}

	/**
	 * 분류 비활성화 검증용(계획 §0-3, §4 aggregate-4).
	 */
	public long countEnabledByCategoryId(String categoryId) {
		return inspectionQuestionRepository.countByCategoryIdAndEnabledTrue(categoryId);
	}

	/**
	 * 분류 안에서의 채번(질문 등록/이동)용.
	 */
	public int findMaxSortOrderInCategory(String categoryId) {
		return inspectionQuestionRepository.findMaxSortOrderByCategoryId(categoryId);
	}

	/**
	 * 분류별 활성 질문 수. categoryId가 null인 질문(미분류 과도기)은 버린다 — 호출자는
	 * getOrDefault(categoryId, 0)으로 특정 분류의 카운트만 조회하면 충분하다.
	 */
	public Map<String, Integer> countEnabledGroupByCategoryId() {
		Map<String, Integer> counts = new HashMap<>();
		for (Object[] row : inspectionQuestionRepository.countEnabledGroupByCategoryId()) {
			if (row[0] == null) {
				continue;
			}
			counts.put((String)row[0], ((Number)row[1]).intValue());
		}
		return counts;
	}
}
