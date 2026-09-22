package com.seoulchonnom.aggregate.inspection.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.QuestionChoiceSdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 질문 마스터와 그 버전 이력을 관리한다.
 *
 * 쓰기는 ADMIN 전용이지만 조회는 USER도 쓴다 — 매물 생성 시 활성 질문 목록이 필요하다.
 * answerType은 등록 시에만 정할 수 있고, 문구 변경은 기존 버전을 건드리지 않고 새 버전을 덧붙인다.
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
	private final InspectionQuestionMapper inspectionQuestionMapper;
	private final IdGenerator idGenerator;

	public List<InspectionQuestionRdo> getInspectionQuestions(boolean includeDisabled) {
		List<InspectionQuestion> questions = includeDisabled
			? inspectionQuestionStore.findAll()
			: inspectionQuestionStore.findAllEnabled();
		return questions.stream()
			.map(question -> inspectionQuestionMapper.toInspectionQuestionRdo(question, null))
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

	@Transactional
	public InspectionQuestionRdo registerInspectionQuestion(InspectionQuestionCdo inspectionQuestionCdo) {
		if (inspectionQuestionCdo.getAnswerType() == null) {
			throw new InvalidInspectionQuestionException("질문 타입은 필수입니다.");
		}
		validateContent(inspectionQuestionCdo.getAnswerType(), inspectionQuestionCdo.getContent(),
			inspectionQuestionCdo.getDescription(), inspectionQuestionCdo.getChoices(),
			inspectionQuestionCdo.getUnit());

		String questionId = idGenerator.nextDomainId(SequenceName.INSPECTION_QUESTION.toString());
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion(questionId,
			inspectionQuestionCdo);
		return inspectionQuestionMapper.toInspectionQuestionRdo(inspectionQuestionStore.save(question), null);
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
		return inspectionQuestionMapper.toInspectionQuestionRdo(inspectionQuestionStore.save(question), null);
	}

	@Transactional
	public InspectionQuestionRdo modifyInspectionQuestionPolicy(String questionId,
		InspectionQuestionPolicyUdo inspectionQuestionPolicyUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		question.changePolicy(inspectionQuestionPolicyUdo.isRequired(), inspectionQuestionPolicyUdo.getSortOrder());
		return inspectionQuestionMapper.toInspectionQuestionRdo(inspectionQuestionStore.save(question), null);
	}

	@Transactional
	public InspectionQuestionRdo changeInspectionQuestionStatus(String questionId,
		InspectionQuestionStatusUdo inspectionQuestionStatusUdo) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		question.changeEnabled(inspectionQuestionStatusUdo.isEnabled());
		return inspectionQuestionMapper.toInspectionQuestionRdo(inspectionQuestionStore.save(question), null);
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
