package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidViewedPropertyException;
import com.seoulchonnom.aggregate.inspection.exception.ReviewSuggestionUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.suggestion.DisabledReviewSuggestionGenerator;
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

class InspectionReviewSuggestionFlowTest {
	private static final String VISIT_ID = "visit-1";
	private static final String PROPERTY_ID = "prop-1";

	private final InspectionVisitLogic inspectionVisitLogic = mock(InspectionVisitLogic.class);
	private final InspectionAreaLogic inspectionAreaLogic = mock(InspectionAreaLogic.class);
	private final ViewedPropertyLogic viewedPropertyLogic = mock(ViewedPropertyLogic.class);
	private final InspectionTagLogic inspectionTagLogic = mock(InspectionTagLogic.class);
	private final ReviewSuggestionGenerator generator = mock(ReviewSuggestionGenerator.class);
	private final InspectionReviewSuggestionFlow flow = new InspectionReviewSuggestionFlow(inspectionVisitLogic,
		inspectionAreaLogic, viewedPropertyLogic, inspectionTagLogic, new InspectionTagMapper(),
		new ReviewSuggestionPromptBuilder(), generator);

	private ViewedProperty property(String visitId) {
		ViewedProperty property = new ViewedProperty(visitId, "트리마제", "101동", 1);
		property.setId(PROPERTY_ID);
		return property;
	}

	private void givenVisit() {
		InspectionVisit visit = new InspectionVisit(VISIT_ID, "area-1", LocalDateTime.of(2026, 9, 17, 14, 0));
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(visit);
		when(inspectionAreaLogic.getInspectionArea("area-1")).thenReturn(new InspectionArea("area-1", "성수동", null));
		when(inspectionTagLogic.getInspectionTags(any(), any())).thenReturn(List.of());
	}

	@Test
	void suggestVisitReview_shouldNormalizeGeneratorOutput() {
		givenVisit();
		String longTag = "가".repeat(51);
		List<String> rawTags = new ArrayList<>(List.of(" #한강 ", "한강", "  ", longTag, "조용함"));
		rawTags.add(null);
		when(generator.generate(any())).thenReturn(new ReviewSuggestion(
			"  " + "가".repeat(400) + "  ", " - 주차 불편 \n", rawTags));

		ReviewSuggestionRdo result = flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", null));

		assertThat(result.getOneLineReview()).hasSize(300);
		assertThat(result.getCons()).isEqualTo("- 주차 불편");
		assertThat(result.getTags()).containsExactly("한강", "조용함");
	}

	@Test
	void suggestVisitReview_shouldCapTagsAtTenAndTurnNullsIntoEmpty() {
		givenVisit();
		List<String> many = IntStream.range(0, 15).mapToObj(i -> "태그" + i).toList();
		when(generator.generate(any())).thenReturn(new ReviewSuggestion(null, null, many));

		ReviewSuggestionRdo capped = flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", null));
		assertThat(capped.getTags()).hasSize(10);
		assertThat(capped.getOneLineReview()).isEmpty();
		assertThat(capped.getCons()).isEmpty();

		when(generator.generate(any())).thenReturn(new ReviewSuggestion(null, null, null));
		assertThat(flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo(null, "장점")).getTags()).isEmpty();
	}

	@Test
	void suggestVisitReview_shouldRejectBlankMemoAndProsWithoutCallingGenerator() {
		givenVisit();

		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("  ", null)))
			.isInstanceOf(InvalidInspectionVisitException.class);
		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo()))
			.isInstanceOf(InvalidInspectionVisitException.class);
		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, null))
			.isInstanceOf(InvalidInspectionVisitException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void suggestVisitReview_shouldRejectTooLongInput() {
		givenVisit();

		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("가".repeat(5001), null)))
			.isInstanceOf(InvalidInspectionVisitException.class);
		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", "가".repeat(5001))))
			.isInstanceOf(InvalidInspectionVisitException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void suggestVisitReview_shouldPropagateNotFoundWhenVisitMissing() {
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenThrow(new InspectionVisitNotFoundException());

		assertThatThrownBy(() -> flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", null)))
			.isInstanceOf(InspectionVisitNotFoundException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void suggestVisitReview_shouldSendTopTagPoolOfVisitScope() {
		givenVisit();
		List<InspectionTagRdo> tags = IntStream.range(0, 50)
			.mapToObj(i -> new InspectionTagRdo("id" + i, "tag" + i, 50 - i))
			.toList();
		when(inspectionTagLogic.getInspectionTags(null, InspectionTagScope.VISIT)).thenReturn(tags);
		when(generator.generate(any())).thenReturn(new ReviewSuggestion("", "", List.of()));

		flow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", null));

		ArgumentCaptor<ReviewSuggestionPrompt> captor = ArgumentCaptor.forClass(ReviewSuggestionPrompt.class);
		verify(generator).generate(captor.capture());
		assertThat(captor.getValue().content()).contains("tag29").doesNotContain("tag30");
	}

	@Test
	void suggestPropertyReview_shouldRejectPropertyOfAnotherVisit() {
		givenVisit();
		when(viewedPropertyLogic.getViewedProperty(PROPERTY_ID)).thenReturn(property("other-visit"));

		assertThatThrownBy(() -> flow.suggestPropertyReview(VISIT_ID, PROPERTY_ID, new ReviewSuggestionSdo("메모", null)))
			.isInstanceOf(ViewedPropertyNotFoundException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void suggestPropertyReview_shouldRejectBlankMemoAndPros() {
		givenVisit();
		when(viewedPropertyLogic.getViewedProperty(PROPERTY_ID)).thenReturn(property(VISIT_ID));

		assertThatThrownBy(() -> flow.suggestPropertyReview(VISIT_ID, PROPERTY_ID, new ReviewSuggestionSdo(" ", "")))
			.isInstanceOf(InvalidViewedPropertyException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void suggestPropertyReview_shouldUsePropertyScopeTagsAndReturnSuggestion() {
		givenVisit();
		when(viewedPropertyLogic.getViewedProperty(PROPERTY_ID)).thenReturn(property(VISIT_ID));
		when(inspectionTagLogic.getInspectionTags(null, InspectionTagScope.PROPERTY))
			.thenReturn(List.of(new InspectionTagRdo("t1", "남향", 3)));
		when(generator.generate(any())).thenReturn(new ReviewSuggestion("채광 좋음", "", List.of("남향")));

		ReviewSuggestionRdo result = flow.suggestPropertyReview(VISIT_ID, PROPERTY_ID,
			new ReviewSuggestionSdo(null, "채광"));

		assertThat(result.getTags()).containsExactly("남향");
		ArgumentCaptor<ReviewSuggestionPrompt> captor = ArgumentCaptor.forClass(ReviewSuggestionPrompt.class);
		verify(generator).generate(captor.capture());
		assertThat(captor.getValue().content()).contains("태그 후보: 남향", "트리마제");
	}

	@Test
	void suggestVisitReview_shouldFailWhenGeneratorIsDisabled() {
		InspectionReviewSuggestionFlow disabledFlow = new InspectionReviewSuggestionFlow(inspectionVisitLogic,
			inspectionAreaLogic, viewedPropertyLogic, inspectionTagLogic, new InspectionTagMapper(),
			new ReviewSuggestionPromptBuilder(), new DisabledReviewSuggestionGenerator());
		givenVisit();

		assertThatThrownBy(() -> disabledFlow.suggestVisitReview(VISIT_ID, new ReviewSuggestionSdo("메모", null)))
			.isInstanceOf(ReviewSuggestionUnavailableException.class)
			.hasMessage("AI 후기 제안 설정에 문제가 있어 사용할 수 없습니다. 관리자에게 문의하세요.");
	}
}
