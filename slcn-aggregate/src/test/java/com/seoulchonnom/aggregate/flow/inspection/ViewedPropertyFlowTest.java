package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionOrderException;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.ComplexNameScope;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerBulkUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

class ViewedPropertyFlowTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";

	private final ViewedPropertyLogic viewedPropertyLogic = mock(ViewedPropertyLogic.class);
	private final InspectionVisitLogic inspectionVisitLogic = mock(InspectionVisitLogic.class);
	private final InspectionQuestionLogic inspectionQuestionLogic = mock(InspectionQuestionLogic.class);
	private final InspectionTagLogic inspectionTagLogic = mock(InspectionTagLogic.class);
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final InspectionPhotoSupport inspectionPhotoSupport = mock(InspectionPhotoSupport.class);
	private final ViewedPropertyFlow viewedPropertyFlow = new ViewedPropertyFlow(viewedPropertyLogic,
		inspectionVisitLogic, inspectionQuestionLogic, inspectionTagLogic, inspectionTagStore,
		inspectionPhotoSupport);

	private static ViewedProperty property(String id, InspectionStatus status, int sortOrder) {
		ViewedProperty property = new ViewedProperty(VISIT_ID, "트리마제", "101동 1203호", sortOrder);
		property.setId(id);
		property.changeStatus(status);
		return property;
	}

	private static InspectionVisit visit(InspectionStatus status) {
		InspectionVisit visit = new InspectionVisit(VISIT_ID, "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		visit.changeStatus(status);
		return visit;
	}

	private static ViewedPropertyCdo cdo() {
		ViewedPropertyCdo cdo = new ViewedPropertyCdo();
		cdo.setComplexName("트리마제");
		cdo.setName("101동 1203호");
		return cdo;
	}

	private void echoSave() {
		when(viewedPropertyLogic.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registerViewedProperty_shouldNumberSortOrderAfterExistingTail() {
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(visit(InspectionStatus.DRAFT));
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID))
			.thenReturn(List.of(property("p1", InspectionStatus.DRAFT, 3)));
		when(inspectionQuestionLogic.getEnabledQuestions()).thenReturn(List.of());
		echoSave();

		ViewedProperty saved = viewedPropertyFlow.registerViewedProperty(VISIT_ID, cdo());

		assertThat(saved.getSortOrder()).isEqualTo(4);
		assertThat(saved.getStatus()).isEqualTo(InspectionStatus.DRAFT);
	}

	@Test
	void registerViewedProperty_shouldMaterializeAnswersBeforeSaving() {
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(visit(InspectionStatus.DRAFT));
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID)).thenReturn(List.of());
		when(inspectionQuestionLogic.getEnabledQuestions()).thenReturn(List.of());
		echoSave();

		viewedPropertyFlow.registerViewedProperty(VISIT_ID, cdo());

		InOrder order = inOrder(viewedPropertyLogic);
		order.verify(viewedPropertyLogic).materializeAnswers(any(), anyList());
		order.verify(viewedPropertyLogic).save(any());
	}

	@Test
	void registerViewedProperty_shouldRevertCompletedVisitToDraft() {
		InspectionVisit completedVisit = visit(InspectionStatus.COMPLETED);
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(completedVisit);
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID)).thenReturn(List.of());
		when(inspectionQuestionLogic.getEnabledQuestions()).thenReturn(List.of());
		echoSave();

		viewedPropertyFlow.registerViewedProperty(VISIT_ID, cdo());

		// 새 매물은 DRAFT라 "모든 매물 COMPLETED" 조건을 깬다. 409 대신 임장이 따라 내려간다
		assertThat(completedVisit.getStatus()).isEqualTo(InspectionStatus.DRAFT);
		verify(inspectionVisitLogic).save(completedVisit);
	}

	@Test
	void registerViewedProperty_shouldLeaveDraftVisitAlone() {
		InspectionVisit draftVisit = visit(InspectionStatus.DRAFT);
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(draftVisit);
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID)).thenReturn(List.of());
		when(inspectionQuestionLogic.getEnabledQuestions()).thenReturn(List.of());
		echoSave();

		viewedPropertyFlow.registerViewedProperty(VISIT_ID, cdo());

		verify(inspectionVisitLogic, never()).save(any());
	}

	@Test
	void modifyViewedProperty_shouldRejectPropertyOfAnotherVisit() {
		ViewedProperty foreign = property("p1", InspectionStatus.DRAFT, 1);
		foreign.setInspectionVisitId("INSPECTION_VISIT-9999");
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(foreign);

		assertThatThrownBy(() -> viewedPropertyFlow.modifyViewedProperty(VISIT_ID, "p1", new ViewedPropertyUdo()))
			.isInstanceOf(ViewedPropertyNotFoundException.class);
	}

	@Test
	void modifyViewedProperty_shouldRevalidateBeforeSaving() {
		ViewedProperty property = property("p1", InspectionStatus.COMPLETED, 1);
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property);
		echoSave();

		viewedPropertyFlow.modifyViewedProperty(VISIT_ID, "p1", new ViewedPropertyUdo());

		InOrder order = inOrder(viewedPropertyLogic);
		order.verify(viewedPropertyLogic).revalidateIfCompleted(property);
		order.verify(viewedPropertyLogic).save(property);
	}

	@Test
	void modifyPropertyAnswers_shouldRevalidateLikeTheBasicInfoPath() {
		ViewedProperty property = property("p1", InspectionStatus.COMPLETED, 1);
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property);
		echoSave();

		viewedPropertyFlow.modifyPropertyAnswers(VISIT_ID, "p1", new PropertyAnswerBulkUdo());

		InOrder order = inOrder(viewedPropertyLogic);
		order.verify(viewedPropertyLogic).applyAnswers(eq(property), any());
		order.verify(viewedPropertyLogic).revalidateIfCompleted(property);
		order.verify(viewedPropertyLogic).save(property);
	}

	@Test
	void modifyViewedProperty_shouldKeepTagsWhenOmitted() {
		ViewedProperty property = property("p1", InspectionStatus.DRAFT, 1);
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property);
		echoSave();

		viewedPropertyFlow.modifyViewedProperty(VISIT_ID, "p1", new ViewedPropertyUdo());

		verifyNoInteractions(inspectionTagStore);
	}

	@Test
	void changeViewedPropertyStatus_shouldValidateBeforeCompleting() {
		ViewedProperty property = property("p1", InspectionStatus.DRAFT, 1);
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property);
		echoSave();

		viewedPropertyFlow.changeViewedPropertyStatus(VISIT_ID, "p1", InspectionStatus.COMPLETED);

		verify(viewedPropertyLogic).validateCompletable(property);
		assertThat(property.getStatus()).isEqualTo(InspectionStatus.COMPLETED);
	}

	@Test
	void changeViewedPropertyStatus_shouldRevertVisitWhenGoingBackToDraft() {
		ViewedProperty property = property("p1", InspectionStatus.COMPLETED, 1);
		InspectionVisit completedVisit = visit(InspectionStatus.COMPLETED);
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property);
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(completedVisit);
		echoSave();

		viewedPropertyFlow.changeViewedPropertyStatus(VISIT_ID, "p1", InspectionStatus.DRAFT);

		assertThat(completedVisit.getStatus()).isEqualTo(InspectionStatus.DRAFT);
	}

	@Test
	void deleteViewedProperty_shouldRemoveRdbLinksBeforePhotos() {
		when(viewedPropertyLogic.getViewedProperty("p1")).thenReturn(property("p1", InspectionStatus.DRAFT, 1));

		viewedPropertyFlow.deleteViewedProperty(VISIT_ID, "p1");

		InOrder order = inOrder(inspectionTagStore, viewedPropertyLogic, inspectionPhotoSupport);
		order.verify(inspectionTagStore).deletePropertyLinks("p1");
		order.verify(viewedPropertyLogic).deleteViewedProperty("p1");
		order.verify(inspectionPhotoSupport).removePropertyPhotos(VISIT_ID, "p1");
	}

	@Test
	void modifyViewedPropertyOrder_shouldRejectForeignProperty() {
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID))
			.thenReturn(List.of(property("p1", InspectionStatus.DRAFT, 1)));

		assertThatThrownBy(() -> viewedPropertyFlow.modifyViewedPropertyOrder(VISIT_ID,
			List.of(new ViewedPropertyOrderUdo("p1", 2), new ViewedPropertyOrderUdo("p9", 1))))
			.isInstanceOf(InvalidInspectionOrderException.class);
		verify(viewedPropertyLogic, never()).saveAll(anyList());
	}

	@Test
	void modifyViewedPropertyOrder_shouldLeaveOmittedPropertiesUntouched() {
		ViewedProperty first = property("p1", InspectionStatus.DRAFT, 1);
		ViewedProperty second = property("p2", InspectionStatus.DRAFT, 2);
		when(viewedPropertyLogic.getViewedProperties(VISIT_ID)).thenReturn(List.of(first, second));

		viewedPropertyFlow.modifyViewedPropertyOrder(VISIT_ID, List.of(new ViewedPropertyOrderUdo("p2", 5)));

		assertThat(first.getSortOrder()).isEqualTo(1);
		assertThat(second.getSortOrder()).isEqualTo(5);
		verify(viewedPropertyLogic).saveAll(List.of(second));
	}

	@Test
	void getComplexNames_shouldDelegateVisitScopeToLogicWithOnlyThisVisit() {
		when(viewedPropertyLogic.getDistinctComplexNames(List.of(VISIT_ID)))
			.thenReturn(List.of("갤러리아포레", "트리마제"));

		assertThat(viewedPropertyFlow.getComplexNames(VISIT_ID, ComplexNameScope.VISIT))
			.containsExactly("갤러리아포레", "트리마제");
		verifyNoInteractions(inspectionVisitLogic);
	}

	@Test
	void getComplexNames_shouldDelegateAreaScopeToLogicWithAllVisitsOfTheArea() {
		InspectionVisit visit = visit(InspectionStatus.DRAFT);
		InspectionVisit otherVisit = new InspectionVisit("INSPECTION_VISIT-0002", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 6, 2, 10, 0));
		when(inspectionVisitLogic.getInspectionVisit(VISIT_ID)).thenReturn(visit);
		when(inspectionVisitLogic.getInspectionVisitsByAreaId("INSPECTION_AREA-0001"))
			.thenReturn(List.of(visit, otherVisit));
		when(viewedPropertyLogic.getDistinctComplexNames(List.of(VISIT_ID, "INSPECTION_VISIT-0002")))
			.thenReturn(List.of("갤러리아포레", "트리마제"));

		assertThat(viewedPropertyFlow.getComplexNames(VISIT_ID, ComplexNameScope.AREA))
			.containsExactly("갤러리아포레", "트리마제");
	}
}
