package com.seoulchonnom.aggregate.flow.inspection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestion;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionGenerator;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionPrompt;
import com.seoulchonnom.aggregate.inspection.suggestion.ReviewSuggestionPromptBuilder;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionTagScope;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionSdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionTagMapper;

import lombok.RequiredArgsConstructor;

/**
 * 후기(한줄평/장점/단점/태그) 제안. 아무것도 저장하지 않는다.
 *
 * 클래스와 메서드에 @Transactional을 두지 않는다. 모델 호출은 수 초 걸릴 수 있어
 * 트랜잭션(커넥션)을 잡은 채로 기다리지 않도록, 데이터는 각 Logic의 읽기 전용 트랜잭션으로 읽고
 * 끝낸 뒤에 호출한다.
 */
@Service
@RequiredArgsConstructor
public class InspectionReviewSuggestionFlow {
	/**
	 * 한줄평/장점/단점/태그 상한은 저장 쪽 검증(InspectionVisitLogic, InspectionTagLogic)과 같다.
	 * 제안을 그대로 저장해도 저장 API가 거절하지 않도록 맞춘다.
	 */
	static final int MAX_ONE_LINE_REVIEW_LENGTH = 300;
	static final int MAX_TEXT_LENGTH = 5000;
	static final int MAX_TAG_COUNT = 10;
	static final int MAX_TAG_NAME_LENGTH = 50;
	/**
	 * 프롬프트에 싣는 기존 태그 후보 수. 전체 태그를 읽더라도 모델에는 사용 빈도 상위만 보낸다.
	 */
	static final int TAG_POOL_SIZE = 30;

	private final InspectionVisitLogic inspectionVisitLogic;
	private final InspectionAreaLogic inspectionAreaLogic;
	private final ViewedPropertyLogic viewedPropertyLogic;
	private final InspectionTagLogic inspectionTagLogic;
	private final InspectionTagMapper inspectionTagMapper;
	private final ReviewSuggestionPromptBuilder promptBuilder;
	private final ReviewSuggestionGenerator reviewSuggestionGenerator;

	/**
	 * request는 Resource의 @Valid가 검증한 값(memo 필수, 길이 상한)이라는 전제로 받는다.
	 */
	public ReviewSuggestionRdo suggestVisitReview(String visitId, ReviewSuggestionSdo request) {
		InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);
		InspectionArea area = inspectionAreaLogic.getInspectionArea(visit.getAreaId());
		ReviewSuggestionPrompt prompt = promptBuilder.forVisit(visit, area, request.getMemo(), request.getPros(),
			tagPool(InspectionTagScope.VISIT));
		return normalize(reviewSuggestionGenerator.generate(prompt));
	}

	public ReviewSuggestionRdo suggestPropertyReview(String visitId, String propertyId, ReviewSuggestionSdo request) {
		InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);
		ViewedProperty property = viewedPropertyLogic.getViewedProperty(propertyId);
		if (!visitId.equals(property.getInspectionVisitId())) {
			throw new ViewedPropertyNotFoundException("이 임장에 속한 매물이 아닙니다. propertyId=" + propertyId);
		}
		InspectionArea area = inspectionAreaLogic.getInspectionArea(visit.getAreaId());
		ReviewSuggestionPrompt prompt = promptBuilder.forProperty(property, area, visit.getVisitedAt(),
			request.getMemo(), request.getPros(), tagPool(InspectionTagScope.PROPERTY));
		return normalize(reviewSuggestionGenerator.generate(prompt));
	}

	/**
	 * 키워드 없이 태그 풀 전체를 읽고 상위만 자른다. 태그는 사용자가 직접 만든 소량이라 전건 조회를 허용한다.
	 */
	private List<String> tagPool(InspectionTagScope scope) {
		return inspectionTagLogic.getInspectionTags(null, scope).stream()
			.map(InspectionTagRdo::getName)
			.limit(TAG_POOL_SIZE)
			.toList();
	}

	/**
	 * 모델 출력을 저장 가능한 모양으로 다듬는다. 길이 초과는 거절하지 않고 자른다 — 제안일 뿐이라
	 * 사용자가 폼에서 고칠 수 있고, 거절하면 사용자는 아무것도 얻지 못한다.
	 */
	private ReviewSuggestionRdo normalize(ReviewSuggestion suggestion) {
		String oneLineReview = cut(trim(suggestion.oneLineReview()), MAX_ONE_LINE_REVIEW_LENGTH);
		String pros = cut(trim(suggestion.pros()), MAX_TEXT_LENGTH);
		String cons = cut(trim(suggestion.cons()), MAX_TEXT_LENGTH);
		return new ReviewSuggestionRdo(oneLineReview, pros, cons, normalizeTags(suggestion.tags()));
	}

	private List<String> normalizeTags(List<String> rawTags) {
		if (rawTags == null) {
			return List.of();
		}
		Set<String> tags = new LinkedHashSet<>();
		for (String rawTag : rawTags) {
			String name = inspectionTagMapper.normalizeName(rawTag);
			if (name == null || name.length() > MAX_TAG_NAME_LENGTH) {
				continue;
			}
			tags.add(name);
			if (tags.size() >= MAX_TAG_COUNT) {
				break;
			}
		}
		return List.copyOf(tags);
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private String cut(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
	}
}
