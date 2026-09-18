package com.seoulchonnom.aggregate.flow.inspection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 질문 조회에 답변 수를 붙인다. 질문과 매물이라는 두 aggregate를 가로지르므로 Logic이 아니라 여기 둔다.
 *
 * 답변이 매물 행 안의 JSON이라 이 집계만 인덱스를 타지 못하고 전건 스캔이 된다.
 * 관리자 화면 전용이고 기본값이 withAnswerCount=false라 일반 사용자 흐름은 이 경로를 타지 않는다.
 * 매물이 2,000건을 넘으면 inspection_question에 answer_count를 비정규화한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionQuestionQueryFlow {
	private final InspectionQuestionStore inspectionQuestionStore;
	private final ViewedPropertyStore viewedPropertyStore;
	private final InspectionQuestionMapper inspectionQuestionMapper;

	public List<InspectionQuestionRdo> getInspectionQuestions(boolean includeDisabled, boolean withAnswerCount) {
		List<InspectionQuestion> questions = includeDisabled
			? inspectionQuestionStore.findAll()
			: inspectionQuestionStore.findAllEnabled();
		Map<String, Integer> counts = withAnswerCount ? countByQuestionId() : Map.of();

		return questions.stream()
			.map(question -> inspectionQuestionMapper.toInspectionQuestionRdo(question,
				withAnswerCount ? counts.getOrDefault(question.getId(), 0) : null))
			.toList();
	}

	public List<InspectionQuestionVersionRdo> getInspectionQuestionVersions(String questionId) {
		InspectionQuestion question = inspectionQuestionStore.findById(questionId);
		Map<Integer, Integer> counts = countByVersionNo(questionId);
		List<QuestionVersion> versions = question.getVersions() == null ? List.of() : question.getVersions();

		return versions.stream()
			.map(version -> inspectionQuestionMapper.toInspectionQuestionVersionRdo(question, version,
				counts.getOrDefault(version.getVersionNo(), 0)))
			.toList();
	}

	private Map<String, Integer> countByQuestionId() {
		Map<String, Integer> counts = new HashMap<>();
		forEachAnswer(answer -> counts.merge(answer.getQuestionId(), 1, Integer::sum));
		return counts;
	}

	private Map<Integer, Integer> countByVersionNo(String questionId) {
		Map<Integer, Integer> counts = new HashMap<>();
		forEachAnswer(answer -> {
			if (questionId.equals(answer.getQuestionId())) {
				counts.merge(answer.getQuestionVersionNo(), 1, Integer::sum);
			}
		});
		return counts;
	}

	private void forEachAnswer(Consumer<PropertyAnswer> consumer) {
		for (ViewedProperty property : viewedPropertyStore.findAll()) {
			if (property.getAnswers() == null) {
				continue;
			}
			property.getAnswers().forEach(consumer);
		}
	}
}
