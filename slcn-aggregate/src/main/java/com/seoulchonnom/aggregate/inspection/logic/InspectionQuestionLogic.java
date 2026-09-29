package com.seoulchonnom.aggregate.inspection.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDisabledException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryRequiredException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionReactivationBlockedException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryMoveUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.QuestionChoiceSdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionMapper;
import com.seoulchonnom.spec.inspection.util.InspectionQuestionOrdering;

import lombok.RequiredArgsConstructor;

/**
 * 질문 마스터와 그 버전 이력을 관리한다.
 *
 * 쓰기는 ADMIN 전용이지만 조회는 USER도 쓴다 — 매물 생성 시 활성 질문 목록이 필요하다.
 * answerType은 등록 시에만 정할 수 있고, 문구 변경은 기존 버전을 건드리지 않고 새 버전을 덧붙인다.
 * 분류 조회에는 InspectionQuestionCategoryStore를 직접 쓴다 - Logic끼리는 서로 호출하지 않는다
 * (계획 §4 aggregate-6, InspectionQuestionCategoryLogic과 같은 방식).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionQuestionLogic {
	private static final int MAX_CONTENT_LENGTH = 300;
	private static final int MAX_DESCRIPTION_LENGTH = 500;
	private static final int MAX_UNIT_LENGTH = 20;
	private static final int MAX_CHOICE_COUNT = 30;

	private final InspectionQuestionStore inspectionQuestionStore;
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore;
	private final InspectionQuestionMapper inspectionQuestionMapper;
	private final IdGenerator idGenerator;

	public List<InspectionQuestionRdo> getInspectionQuestions(boolean includeDisabled) {
		List<InspectionQuestion> questions = includeDisabled
			? inspectionQuestionStore.findAll()
			: inspectionQuestionStore.findAllEnabled();
		Map<String, InspectionQuestionCategory> categoriesById = categoryMapFor(questions);
		return questions.stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.map(question -> inspectionQuestionMapper.toInspectionQuestionRdo(question,
				categoriesById.get(question.getCategoryId()), null))
			.toList();
	}

	public List<InspectionQuestionVersionRdo> getInspectionQuestionVersions(String questionId) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		return versionsOf(question).stream()
			.map(version -> inspectionQuestionMapper.toInspectionQuestionVersionRdo(question, version, null))
			.toList();
	}

	public List<InspectionQuestion> getEnabledQuestions() {
		return inspectionQuestionStore.findAllEnabled();
	}

	public Map<String, InspectionQuestion> getQuestionMap(List<String> questionIds) {
		return inspectionQuestionStore.findMapByIds(questionIds);
	}

	/**
	 * categoryId는 필수이고 활성 분류만 허용한다(계획 §0-1, §1). sortOrder가 0 이하면
	 * 그 분류 안 맨 뒤(max+1)로 채번한다 - 분류 안에서의 순서라는 의미가 바뀌었기 때문이다(계획 §0-4).
	 */
	@Transactional
	public InspectionQuestionRdo registerInspectionQuestion(InspectionQuestionCdo inspectionQuestionCdo) {
		if (inspectionQuestionCdo.getAnswerType() == null) {
			throw new InvalidInspectionQuestionException("질문 타입은 필수입니다.");
		}
		validateContent(inspectionQuestionCdo.getAnswerType(), inspectionQuestionCdo.getContent(),
			inspectionQuestionCdo.getDescription(), inspectionQuestionCdo.getChoices(),
			inspectionQuestionCdo.getUnit());

		InspectionQuestionCategory category = requireEnabledCategory(inspectionQuestionCdo.getCategoryId());

		String questionId = idGenerator.nextDomainId(SequenceName.INSPECTION_QUESTION.toString());
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion(questionId,
			inspectionQuestionCdo);
		if (inspectionQuestionCdo.getSortOrder() <= 0) {
			question.setSortOrder(inspectionQuestionStore.findMaxSortOrderInCategory(category.getId()) + 1);
		}
		InspectionQuestion saved = inspectionQuestionStore.save(question);
		return inspectionQuestionMapper.toInspectionQuestionRdo(saved, category, null);
	}

	/**
	 * 새 버전을 덧붙인다. 기존 버전은 읽기 전용이다 — 고치면 그 문구로 답한 기록의 의미가 바뀐다.
	 * answerType이 요청에 없는 것이 이 API의 핵심이다.
	 */
	@Transactional
	public InspectionQuestionRdo modifyInspectionQuestionContent(String questionId,
		InspectionQuestionContentUdo inspectionQuestionContentUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		validateContent(question.getAnswerType(), inspectionQuestionContentUdo.getContent(),
			inspectionQuestionContentUdo.getDescription(), inspectionQuestionContentUdo.getChoices(),
			inspectionQuestionContentUdo.getUnit());

		inspectionQuestionMapper.addVersion(question, inspectionQuestionContentUdo);
		return toRdo(inspectionQuestionStore.save(question));
	}

	@Transactional
	public InspectionQuestionRdo modifyInspectionQuestionPolicy(String questionId,
		InspectionQuestionPolicyUdo inspectionQuestionPolicyUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		question.changePolicy(inspectionQuestionPolicyUdo.isRequired(), inspectionQuestionPolicyUdo.getSortOrder());
		return toRdo(inspectionQuestionStore.save(question));
	}

	/**
	 * 비활성 분류에 속한 질문은 다시 활성화할 수 없다(계획 §1) - 이 규칙이 없으면 분류
	 * 비활성화 조건(결정 3)을 질문 재활성화로 우회할 수 있다.
	 */
	@Transactional
	public InspectionQuestionRdo changeInspectionQuestionStatus(String questionId,
		InspectionQuestionStatusUdo inspectionQuestionStatusUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		if (inspectionQuestionStatusUdo.isEnabled()) {
			InspectionQuestionCategory category = inspectionQuestionCategoryStore.findById(question.getCategoryId());
			if (!category.isEnabled()) {
				throw new InspectionQuestionReactivationBlockedException();
			}
		}
		question.changeEnabled(inspectionQuestionStatusUdo.isEnabled());
		return toRdo(inspectionQuestionStore.save(question));
	}

	/**
	 * 대상 분류의 맨 뒤(max+1)로 옮긴다. 이미 그 분류에 있으면 재배치 없이 현재 상태를 그대로
	 * 돌려준다 - 불필요한 저장으로 sortOrder가 흔들리지 않게 한다. 버전은 올리지 않는다(계획 §1).
	 */
	@Transactional
	public InspectionQuestionRdo moveInspectionQuestionCategory(String questionId,
		InspectionQuestionCategoryMoveUdo inspectionQuestionCategoryMoveUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		InspectionQuestionCategory targetCategory = requireEnabledCategory(
			inspectionQuestionCategoryMoveUdo.getCategoryId());

		if (targetCategory.getId().equals(question.getCategoryId())) {
			return inspectionQuestionMapper.toInspectionQuestionRdo(question, targetCategory, null);
		}

		int sortOrder = inspectionQuestionStore.findMaxSortOrderInCategory(targetCategory.getId()) + 1;
		question.moveCategory(targetCategory.getId(), sortOrder);
		InspectionQuestion saved = inspectionQuestionStore.save(question);
		return inspectionQuestionMapper.toInspectionQuestionRdo(saved, targetCategory, null);
	}

	/**
	 * 요청에 빠진 질문은 기존 순서를 유지한다.
	 */
	@Transactional
	public void modifyInspectionQuestionOrder(List<InspectionQuestionOrderUdo> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		Map<String, Integer> requested = new HashMap<>();
		for (InspectionQuestionOrderUdo order : orders) {
			if (!StringUtils.hasText(order.getQuestionId())) {
				throw new InvalidInspectionQuestionException("정렬 대상 질문 ID가 비어 있습니다.");
			}
			requested.put(order.getQuestionId(), order.getSortOrder());
		}

		List<InspectionQuestion> questions = inspectionQuestionStore.findAllByIds(requested.keySet());
		if (questions.size() != requested.size()) {
			throw new InvalidInspectionQuestionException("존재하지 않는 질문이 정렬 요청에 포함되었습니다.");
		}
		questions.forEach(question -> question.changeSortOrder(requested.get(question.getId())));
		inspectionQuestionStore.saveAll(questions);
	}

	/**
	 * 등록/이동에서 함께 쓰는 검증. 누락, 없음(Store가 NotFound를 던진다), 비활성을
	 * 서로 다른 코드로 막아 FE가 코드만 보고 구분할 수 있게 한다.
	 */
	private InspectionQuestionCategory requireEnabledCategory(String categoryId) {
		if (!StringUtils.hasText(categoryId)) {
			throw new InspectionQuestionCategoryRequiredException();
		}
		InspectionQuestionCategory category = inspectionQuestionCategoryStore.findById(categoryId);
		if (!category.isEnabled()) {
			throw new InspectionQuestionCategoryDisabledException();
		}
		return category;
	}

	private InspectionQuestionRdo toRdo(InspectionQuestion question) {
		InspectionQuestionCategory category = inspectionQuestionCategoryStore.findById(question.getCategoryId());
		return inspectionQuestionMapper.toInspectionQuestionRdo(question, category, null);
	}

	private Map<String, InspectionQuestionCategory> categoryMapFor(List<InspectionQuestion> questions) {
		return inspectionQuestionCategoryStore.findMapByIds(
			questions.stream().map(InspectionQuestion::getCategoryId).collect(Collectors.toSet()));
	}

	private List<QuestionVersion> versionsOf(InspectionQuestion question) {
		return question.getVersions() == null ? List.of() : question.getVersions();
	}

	/**
	 * 타입과 맞지 않는 값이 오면 조용히 버리지 않고 막는다. 답변 값 검증(본문 §5.5)과 같은 방향이다.
	 */
	private void validateContent(QuestionAnswerType answerType, String content, String description,
		List<QuestionChoiceSdo> choices, String unit) {
		if (!StringUtils.hasText(content)) {
			throw new InvalidInspectionQuestionException("질문 문구는 필수입니다.");
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new InvalidInspectionQuestionException("질문 문구가 너무 깁니다.");
		}
		if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new InvalidInspectionQuestionException("질문 설명이 너무 깁니다.");
		}
		validateChoices(answerType, choices);
		validateUnit(answerType, unit);
	}

	private void validateChoices(QuestionAnswerType answerType, List<QuestionChoiceSdo> choices) {
		List<QuestionChoiceSdo> given = choices == null ? new ArrayList<>() : choices;
		if (!isSelectType(answerType)) {
			if (!given.isEmpty()) {
				throw new InvalidInspectionQuestionException("선택지는 선택형 질문에서만 쓸 수 있습니다.");
			}
			return;
		}
		if (given.isEmpty()) {
			throw new InvalidInspectionQuestionException("선택형 질문에는 선택지가 최소 1개 필요합니다.");
		}
		if (given.size() > MAX_CHOICE_COUNT) {
			throw new InvalidInspectionQuestionException("선택지가 너무 많습니다.");
		}
		Set<String> codes = new HashSet<>();
		for (QuestionChoiceSdo choice : given) {
			if (!StringUtils.hasText(choice.getCode()) || !StringUtils.hasText(choice.getLabel())) {
				throw new InvalidInspectionQuestionException("선택지의 코드와 표시 문구는 필수입니다.");
			}
			if (!codes.add(choice.getCode())) {
				throw new InvalidInspectionQuestionException("선택지 코드가 중복되었습니다.");
			}
		}
	}

	private void validateUnit(QuestionAnswerType answerType, String unit) {
		if (!StringUtils.hasText(unit)) {
			return;
		}
		if (answerType != QuestionAnswerType.NUMBER) {
			throw new InvalidInspectionQuestionException("단위는 숫자형 질문에서만 쓸 수 있습니다.");
		}
		if (unit.length() > MAX_UNIT_LENGTH) {
			throw new InvalidInspectionQuestionException("단위가 너무 깁니다.");
		}
	}

	private boolean isSelectType(QuestionAnswerType answerType) {
		return answerType == QuestionAnswerType.SINGLE_SELECT || answerType == QuestionAnswerType.MULTI_SELECT;
	}
}
