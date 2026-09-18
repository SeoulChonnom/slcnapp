package com.seoulchonnom.aggregate.inspection.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAnswerRequiredException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidPropertyAnswerException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidViewedPropertyException;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;
import com.seoulchonnom.spec.inspection.mapper.PropertyAnswerMapper;

import lombok.RequiredArgsConstructor;

/**
 * 매물 한 건과 그 안의 문답을 다룬다.
 *
 * 답변 값 검증은 질문 마스터의 현재 타입이 아니라 답변에 박힌 스냅샷 타입으로 한다.
 * 그래야 관리자가 질문을 고쳐도 작성 중인 매물의 검증 기준이 흔들리지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ViewedPropertyLogic {
	private static final int MAX_TEXT_LENGTH = 500;
	private static final int MAX_LONG_TEXT_LENGTH = 5000;
	private static final int MAX_ONE_LINE_REVIEW_LENGTH = 300;
	/**
	 * viewed_property.complex_name / name 컬럼 길이와 같다. 여기서 막지 않으면
	 * flush 시점 DataIntegrityViolationException이 500으로 새어나간다.
	 */
	private static final int MAX_NAME_LENGTH = 200;
	private static final int MIN_INTEREST_LEVEL = 1;
	private static final int MAX_INTEREST_LEVEL = 5;
	private static final int MIN_RATING = 1;
	private static final int MAX_RATING = 5;

	private final ViewedPropertyStore viewedPropertyStore;
	private final PropertyAnswerMapper propertyAnswerMapper;

	public ViewedProperty getViewedProperty(String propertyId) {
		return viewedPropertyStore.findById(propertyId);
	}

	public List<ViewedProperty> getViewedProperties(String inspectionVisitId) {
		return viewedPropertyStore.findAllByVisitId(inspectionVisitId);
	}

	@Transactional
	public ViewedProperty save(ViewedProperty property) {
		return viewedPropertyStore.save(property);
	}

	@Transactional
	public List<ViewedProperty> saveAll(List<ViewedProperty> properties) {
		return viewedPropertyStore.saveAll(properties);
	}

	@Transactional
	public void deleteViewedProperty(String propertyId) {
		viewedPropertyStore.delete(propertyId);
	}

	@Transactional
	public void deleteByVisitId(String inspectionVisitId) {
		viewedPropertyStore.deleteByVisitId(inspectionVisitId);
	}

	public long countByVisitId(String inspectionVisitId) {
		return viewedPropertyStore.countByVisitId(inspectionVisitId);
	}

	/**
	 * 매물 생성 시점의 활성 질문을 그대로 복사해 빈 답변 목록을 만든다.
	 * 이후 추가된 질문은 이 매물에 항목이 없으므로 화면에도 검증에도 등장하지 않는다.
	 *
	 * 활성 질문이 0개여도 매물 생성은 허용한다. 필수 문답 검증이 자동으로 통과할 뿐이다.
	 */
	public void materializeAnswers(ViewedProperty property, List<InspectionQuestion> enabledQuestions) {
		List<PropertyAnswer> answers = new ArrayList<>();
		for (InspectionQuestion question : enabledQuestions) {
			QuestionVersion version = question.currentVersion().orElse(null);
			if (version == null) {
				// 버전 없는 질문은 물어볼 문구가 없다. 스냅샷에서 제외한다
				continue;
			}
			answers.add(propertyAnswerMapper.toPropertyAnswer(question, version));
		}
		property.setAnswers(answers);
		property.refreshAnswerCounts();
	}

	/**
	 * 요청에 없는 questionId의 항목은 그대로 둔다(부분 저장).
	 */
	public void applyAnswers(ViewedProperty property, List<PropertyAnswerUdo> answerUdos) {
		if (answerUdos == null || answerUdos.isEmpty()) {
			return;
		}
		for (PropertyAnswerUdo answerUdo : answerUdos) {
			PropertyAnswer answer = property.findAnswer(answerUdo.getQuestionId())
				.orElseThrow(() -> new InspectionQuestionNotFoundException(
					"이 매물의 문답에 없는 questionId입니다. questionId=" + answerUdo.getQuestionId()));
			applyAnswer(answer, answerUdo);
		}
		property.refreshAnswerCounts();
	}

	public void applyUpdate(ViewedProperty property, ViewedPropertyUdo viewedPropertyUdo) {
		String complexName = requireText(viewedPropertyUdo.getComplexName(), "단지/건물명은 필수입니다.", "단지/건물명이 너무 깁니다.");
		String name = requireText(viewedPropertyUdo.getName(), "매물명은 필수입니다.", "매물명이 너무 깁니다.");
		validateInterestLevel(viewedPropertyUdo.getInterestLevel());
		validateTexts(viewedPropertyUdo.getOneLineReview(), viewedPropertyUdo.getMemo(), viewedPropertyUdo.getPros(),
			viewedPropertyUdo.getCons());

		property.update(complexName, name, trimToNull(viewedPropertyUdo.getMemo()),
			trimToNull(viewedPropertyUdo.getOneLineReview()), trimToNull(viewedPropertyUdo.getPros()),
			trimToNull(viewedPropertyUdo.getCons()), viewedPropertyUdo.getInterestLevel());
	}

	/**
	 * 요구사항 §37의 매물 완료 조건. 미충족 필드 목록을 돌려준다.
	 * 필수 문답은 매물 행 자신의 파생 컬럼으로 판정하므로 다른 테이블을 읽지 않는다.
	 */
	public List<String> findMissingFieldsForCompletion(ViewedProperty property) {
		List<String> missing = new ArrayList<>();
		if (!StringUtils.hasText(property.getComplexName())) {
			missing.add("complexName");
		}
		if (!StringUtils.hasText(property.getName())) {
			missing.add("name");
		}
		if (property.getInterestLevel() == null || property.getInterestLevel() < MIN_INTEREST_LEVEL
			|| property.getInterestLevel() > MAX_INTEREST_LEVEL) {
			missing.add("interestLevel");
		}
		return missing;
	}

	public List<PropertyAnswer> findUnansweredRequired(ViewedProperty property) {
		if (property.getAnswers() == null) {
			return List.of();
		}
		return property.getAnswers().stream()
			.filter(PropertyAnswer::isRequired)
			.filter(answer -> !answer.isAnswered())
			.toList();
	}

	/**
	 * 요구사항 §37을 만족하는지. 완료 전이와 완료 이후 재검증이 이 하나를 공유한다.
	 */
	public void validateCompletable(ViewedProperty property) {
		List<String> missing = findMissingFieldsForCompletion(property);
		if (!missing.isEmpty()) {
			throw new InvalidViewedPropertyException("매물 완료 조건을 만족하지 않습니다. missingFields=" + missing);
		}
		if (property.getUnansweredRequiredCount() > 0) {
			List<String> questionIds = findUnansweredRequired(property).stream()
				.map(PropertyAnswer::getQuestionId)
				.toList();
			throw new InspectionAnswerRequiredException(
				"필수 문답이 완료되지 않았습니다. questionIds=" + questionIds);
		}
	}

	/**
	 * COMPLETED 매물의 상태를 깨뜨릴 수 있는 모든 쓰기 경로가 이 메서드를 공유한다.
	 * 기본 정보 PUT과 문답 PUT 둘 다 필수 문답 조건을 깰 수 있는데, 문답 쪽에 재검증이 없으면
	 * 필수 문항이 빈 COMPLETED 매물이 경고 없이 영구히 남는다.
	 */
	public void revalidateIfCompleted(ViewedProperty property) {
		if (InspectionStatus.COMPLETED == property.getStatus()) {
			validateCompletable(property);
		}
	}

	private void applyAnswer(PropertyAnswer answer, PropertyAnswerUdo answerUdo) {
		if (answer.getAnswerType() == null) {
			throw new InvalidPropertyAnswerException("답변 타입 스냅샷이 없습니다.");
		}
		switch (answer.getAnswerType()) {
			case TEXT -> applyText(answer, answerUdo, MAX_TEXT_LENGTH);
			case LONG_TEXT -> applyText(answer, answerUdo, MAX_LONG_TEXT_LENGTH);
			case BOOLEAN -> {
				rejectOthers(answerUdo, "booleanValue");
				answer.setBooleanValue(answerUdo.getBooleanValue());
			}
			case NUMBER -> {
				rejectOthers(answerUdo, "numberValue");
				answer.setNumberValue(answerUdo.getNumberValue());
			}
			case RATING -> {
				rejectOthers(answerUdo, "ratingValue");
				Integer rating = answerUdo.getRatingValue();
				if (rating != null && (rating < MIN_RATING || rating > MAX_RATING)) {
					throw new InvalidPropertyAnswerException("점수는 1~5 사이여야 합니다.");
				}
				answer.setRatingValue(rating);
			}
			case SINGLE_SELECT -> applySelect(answer, answerUdo, 1);
			case MULTI_SELECT -> applySelect(answer, answerUdo, Integer.MAX_VALUE);
		}
		clearUnusedValues(answer);
		answer.refreshAnswered();
	}

	private void applyText(PropertyAnswer answer, PropertyAnswerUdo answerUdo, int maxLength) {
		rejectOthers(answerUdo, "textValue");
		String value = answerUdo.getTextValue();
		if (value != null && value.length() > maxLength) {
			throw new InvalidPropertyAnswerException("답변이 너무 깁니다.");
		}
		answer.setTextValue(value);
	}

	private void applySelect(PropertyAnswer answer, PropertyAnswerUdo answerUdo, int maxSize) {
		rejectOthers(answerUdo, "selectedCodes");
		List<String> codes = answerUdo.getSelectedCodes() == null ? List.of() : answerUdo.getSelectedCodes();
		if (codes.size() > maxSize) {
			throw new InvalidPropertyAnswerException("단일 선택 질문에는 값을 하나만 보낼 수 있습니다.");
		}
		Set<String> allowed = new HashSet<>();
		if (answer.getChoiceOptions() != null) {
			answer.getChoiceOptions().stream().map(QuestionChoice::getCode).forEach(allowed::add);
		}
		Set<String> seen = new HashSet<>();
		for (String code : codes) {
			if (!allowed.contains(code)) {
				throw new InvalidPropertyAnswerException("선택지에 없는 값입니다. code=" + code);
			}
			if (!seen.add(code)) {
				throw new InvalidPropertyAnswerException("선택지가 중복되었습니다. code=" + code);
			}
		}
		answer.setSelectedCodes(new ArrayList<>(codes));
	}

	/**
	 * 타입에 맞지 않는 값 필드가 함께 오면 400. 조용히 무시하면 FE의 버그가 드러나지 않는다.
	 */
	private void rejectOthers(PropertyAnswerUdo answerUdo, String allowedField) {
		boolean hasText = answerUdo.getTextValue() != null;
		boolean hasBoolean = answerUdo.getBooleanValue() != null;
		boolean hasNumber = answerUdo.getNumberValue() != null;
		boolean hasRating = answerUdo.getRatingValue() != null;
		boolean hasCodes = answerUdo.getSelectedCodes() != null && !answerUdo.getSelectedCodes().isEmpty();

		boolean unexpected = (hasText && !"textValue".equals(allowedField))
			|| (hasBoolean && !"booleanValue".equals(allowedField))
			|| (hasNumber && !"numberValue".equals(allowedField))
			|| (hasRating && !"ratingValue".equals(allowedField))
			|| (hasCodes && !"selectedCodes".equals(allowedField));
		if (unexpected) {
			throw new InvalidPropertyAnswerException("질문 타입과 맞지 않는 값이 함께 전달되었습니다.");
		}
	}

	private void clearUnusedValues(PropertyAnswer answer) {
		switch (answer.getAnswerType()) {
			case TEXT, LONG_TEXT -> clear(answer, true, false, false, false, false);
			case BOOLEAN -> clear(answer, false, true, false, false, false);
			case NUMBER -> clear(answer, false, false, true, false, false);
			case RATING -> clear(answer, false, false, false, true, false);
			case SINGLE_SELECT, MULTI_SELECT -> clear(answer, false, false, false, false, true);
		}
	}

	private void clear(PropertyAnswer answer, boolean keepText, boolean keepBoolean, boolean keepNumber,
		boolean keepRating, boolean keepCodes) {
		if (!keepText) {
			answer.setTextValue(null);
		}
		if (!keepBoolean) {
			answer.setBooleanValue(null);
		}
		if (!keepNumber) {
			answer.setNumberValue(null);
		}
		if (!keepRating) {
			answer.setRatingValue(null);
		}
		if (!keepCodes) {
			answer.setSelectedCodes(new ArrayList<>());
		}
	}

	private void validateInterestLevel(Integer interestLevel) {
		if (interestLevel == null) {
			return;
		}
		if (interestLevel < MIN_INTEREST_LEVEL || interestLevel > MAX_INTEREST_LEVEL) {
			throw new InvalidViewedPropertyException("관심도는 1~5 사이여야 합니다.");
		}
	}

	private void validateTexts(String oneLineReview, String memo, String pros, String cons) {
		if (oneLineReview != null && oneLineReview.length() > MAX_ONE_LINE_REVIEW_LENGTH) {
			throw new InvalidViewedPropertyException("한줄평이 너무 깁니다.");
		}
		for (String text : List.of(nullToEmpty(memo), nullToEmpty(pros), nullToEmpty(cons))) {
			if (text.length() > MAX_LONG_TEXT_LENGTH) {
				throw new InvalidViewedPropertyException("입력이 너무 깁니다.");
			}
		}
	}

	private String requireText(String value, String blankMessage, String tooLongMessage) {
		if (!StringUtils.hasText(value)) {
			throw new InvalidViewedPropertyException(blankMessage);
		}
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > MAX_NAME_LENGTH) {
			throw new InvalidViewedPropertyException(tooLongMessage);
		}
		return normalized;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
