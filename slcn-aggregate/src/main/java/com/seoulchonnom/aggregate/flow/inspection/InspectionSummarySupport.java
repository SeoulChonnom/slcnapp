package com.seoulchonnom.aggregate.flow.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.facade.sdo.IncompleteSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.UnansweredQuestionRdo;

/**
 * DRAFT에서 "무엇이 남았는지"를 만든다. 완료 검증과 같은 기준을 써야 하므로 한 곳에 모은다.
 *
 * 요약에 나타나지 않는 완료 조건이 하나라도 있으면 "남은 것 0개인데 완료 버튼이 안 먹는" 상태가 생긴다.
 * 그래서 각 필드가 어느 완료 조건에 대응하는지 주석으로 남긴다.
 */
@Component
public class InspectionSummarySupport {
	/**
	 * 매물 상세용. 미충족 질문의 문구까지 담는다 — 이미 답변을 전부 로드한 상태라 추가 비용이 없다.
	 */
	public IncompleteSummaryRdo ofProperty(ViewedProperty property) {
		IncompleteSummaryRdo summary = new IncompleteSummaryRdo();
		summary.setUnansweredRequiredCount(property.getUnansweredRequiredCount());
		summary.setUnansweredRequiredQuestions(unansweredQuestions(property));
		summary.setMissingFields(missingPropertyFields(property));
		return summary;
	}

	/**
	 * 임장 상세용. 매물 레벨과 임장 레벨을 함께 담는다 — 매물 필드만 담으면
	 * "재방문 의사 미입력" 때문에 임장을 완료할 수 없는 상황을 화면이 설명하지 못한다.
	 */
	public IncompleteSummaryRdo ofVisit(InspectionVisit visit, List<ViewedPropertySummaryPdo> properties) {
		IncompleteSummaryRdo summary = new IncompleteSummaryRdo();
		summary.setUnansweredRequiredCount(properties.stream()
			.mapToInt(ViewedPropertySummaryPdo::getUnansweredRequiredCount)
			.sum());
		summary.setDraftPropertyCount(countDraft(properties));
		summary.setVisitMissingFields(missingVisitFields(visit));
		return summary;
	}

	/**
	 * 상세 화면처럼 매물 엔티티를 이미 로드한 경우에 쓴다. 같은 값을 얻자고 projection을
	 * 한 번 더 조회하지 않는다 — status와 unansweredRequiredCount는 엔티티에도 있다.
	 */
	public IncompleteSummaryRdo ofVisitWithProperties(InspectionVisit visit, List<ViewedProperty> properties) {
		IncompleteSummaryRdo summary = new IncompleteSummaryRdo();
		summary.setUnansweredRequiredCount(properties.stream()
			.mapToInt(ViewedProperty::getUnansweredRequiredCount)
			.sum());
		summary.setDraftPropertyCount((int)properties.stream()
			.filter(property -> InspectionStatus.COMPLETED != property.getStatus())
			.count());
		summary.setVisitMissingFields(missingVisitFields(visit));
		return summary;
	}

	/**
	 * 지역 목록용. 개수만 담는다 — 목록 N행마다 질문 문구를 끌어오면
	 * 정수 합산이던 집계가 답변 본문 조회로 바뀐다.
	 */
	public IncompleteSummaryRdo ofArea(List<InspectionVisit> visits, List<ViewedPropertySummaryPdo> properties) {
		IncompleteSummaryRdo summary = new IncompleteSummaryRdo();
		summary.setUnansweredRequiredCount(properties.stream()
			.mapToInt(ViewedPropertySummaryPdo::getUnansweredRequiredCount)
			.sum());
		summary.setDraftPropertyCount(countDraft(properties));
		summary.setDraftVisitCount((int)visits.stream()
			.filter(visit -> InspectionStatus.COMPLETED != visit.getStatus())
			.count());
		return summary;
	}

	/**
	 * 임장 목록 한 행과 지역 상세의 visits[]용. 개수만 담는다.
	 */
	public IncompleteSummaryRdo ofVisitCountsOnly(InspectionVisit visit, List<ViewedPropertySummaryPdo> properties) {
		return ofVisit(visit, properties);
	}

	/**
	 * 요구사항 §36-2, §36-3에 대응한다.
	 */
	private List<String> missingVisitFields(InspectionVisit visit) {
		List<String> missing = new ArrayList<>();
		if (visit.getVisitedAt() == null) {
			missing.add("visitedAt");
		}
		if (visit.getRevisitIntent() == null) {
			missing.add("revisitIntent");
		}
		return missing;
	}

	/**
	 * 요구사항 §37-1 ~ §37-4에 대응한다.
	 */
	private List<String> missingPropertyFields(ViewedProperty property) {
		List<String> missing = new ArrayList<>();
		if (!StringUtils.hasText(property.getComplexName())) {
			missing.add("complexName");
		}
		if (!StringUtils.hasText(property.getName())) {
			missing.add("name");
		}
		if (property.getInterestLevel() == null || property.getInterestLevel() < 1
			|| property.getInterestLevel() > 5) {
			missing.add("interestLevel");
		}
		return missing;
	}

	/**
	 * 요구사항 §37-5에 대응한다.
	 */
	private List<UnansweredQuestionRdo> unansweredQuestions(ViewedProperty property) {
		if (property.getAnswers() == null) {
			return new ArrayList<>();
		}
		return property.getAnswers().stream()
			.filter(PropertyAnswer::isRequired)
			.filter(answer -> !answer.isAnswered())
			.map(answer -> new UnansweredQuestionRdo(answer.getQuestionId(), answer.getQuestionContent(),
				answer.getSortOrder()))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private int countDraft(List<ViewedPropertySummaryPdo> properties) {
		return (int)properties.stream()
			.filter(property -> InspectionStatus.COMPLETED != property.getStatus())
			.count();
	}
}
