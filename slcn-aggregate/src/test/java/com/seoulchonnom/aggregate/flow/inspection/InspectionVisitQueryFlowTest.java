package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionVisitMapper;
import com.seoulchonnom.spec.inspection.mapper.PropertyAnswerMapper;
import com.seoulchonnom.spec.inspection.mapper.ViewedPropertyMapper;

class InspectionVisitQueryFlowTest {
	private final InspectionVisitStore inspectionVisitStore = mock(InspectionVisitStore.class);
	private final InspectionAreaStore inspectionAreaStore = mock(InspectionAreaStore.class);
	private final ViewedPropertyStore viewedPropertyStore = mock(ViewedPropertyStore.class);
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final InspectionQuestionLogic inspectionQuestionLogic = mock(InspectionQuestionLogic.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = new InspectionVisitQueryFlow(
		inspectionVisitStore, inspectionAreaStore, viewedPropertyStore, inspectionTagStore, inspectionQuestionLogic,
		fileBoxStore, fileAssetStore, new FileBoxMapper(), new InspectionVisitMapper(),
		new ViewedPropertyMapper(new PropertyAnswerMapper()), new InspectionSummarySupport());

	private static InspectionVisit visit(String id, String areaId, LocalDateTime visitedAt,
		InspectionStatus status, RevisitIntent revisitIntent) {
		InspectionVisit visit = new InspectionVisit(id, areaId, visitedAt);
		visit.changeStatus(status);
		visit.setRevisitIntent(revisitIntent);
		return visit;
	}

	private static ViewedPropertySummaryPdo pdo(String id, String visitId, Integer interestLevel, int sortOrder) {
		ViewedPropertySummaryPdo pdo = mock(ViewedPropertySummaryPdo.class);
		when(pdo.getId()).thenReturn(id);
		when(pdo.getInspectionVisitId()).thenReturn(visitId);
		when(pdo.getInterestLevel()).thenReturn(interestLevel);
		when(pdo.getSortOrder()).thenReturn(sortOrder);
		when(pdo.getComplexName()).thenReturn("트리마제");
		when(pdo.getName()).thenReturn("101동 " + id);
		when(pdo.getStatus()).thenReturn(InspectionStatus.DRAFT);
		return pdo;
	}

	private void noExtras() {
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		when(inspectionAreaStore.findAllByIds(anySet()))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());
	}

	@Test
	void topInterestProperty_shouldPreferHigherInterestThenLowerSortOrder() {
		ViewedPropertySummaryPdo low = pdo("p1", "v1", 3, 1);
		ViewedPropertySummaryPdo high = pdo("p2", "v1", 5, 9);
		ViewedPropertySummaryPdo tie = pdo("p3", "v1", 5, 2);

		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of(low, high, tie)).getId()).isEqualTo("p3");
	}

	@Test
	void topInterestProperty_shouldTreatNullInterestAsLowest() {
		ViewedPropertySummaryPdo none = pdo("p1", "v1", null, 1);
		ViewedPropertySummaryPdo some = pdo("p2", "v1", 1, 2);

		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of(none, some)).getId()).isEqualTo("p2");
	}

	@Test
	void topInterestProperty_shouldReturnNullForEmptyVisit() {
		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of())).isNull();
	}

	@Test
	void getInspectionVisits_shouldKeepQueryCountFixedRegardlessOfVisitCount() {
		List<InspectionVisit> visits = List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0), InspectionStatus.DRAFT, null),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 10, 10, 0), InspectionStatus.DRAFT, null),
			visit("v3", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0), InspectionStatus.DRAFT, null));
		when(inspectionVisitStore.findAll()).thenReturn(visits);
		when(viewedPropertyStore.findSummariesByVisitIds(anyList())).thenReturn(List.of());
		noExtras();

		inspectionVisitQueryFlow.getInspectionVisits(null, null, null, null, null, null);

		// 임장이 3건이어도 매물/태그/FileBox 조회는 각각 1회다
		verify(viewedPropertyStore, times(1)).findSummariesByVisitIds(anyList());
		verify(inspectionTagStore, times(1)).findVisitTagNamesByVisitIds(anyList());
		verify(fileBoxStore, times(1)).findAllByOwnerTypeAndOwnerIdIn(any(), anyList());
		verify(inspectionAreaStore, times(1)).findAllByIds(anySet());
	}

	@Test
	void getInspectionVisits_shouldApplyStatusAndRevisitIntentFilters() {
		when(inspectionVisitStore.findAll()).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0), InspectionStatus.COMPLETED,
				RevisitIntent.YES),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 10, 10, 0), InspectionStatus.DRAFT,
				RevisitIntent.NO)));
		when(viewedPropertyStore.findSummariesByVisitIds(anyList())).thenReturn(List.of());
		noExtras();

		List<InspectionVisitRdo> result = inspectionVisitQueryFlow.getInspectionVisits(null,
			InspectionStatus.COMPLETED, RevisitIntent.YES, null, null, null);

		assertThat(result).extracting(InspectionVisitRdo::getInspectionVisitId).containsExactly("v1");
	}

	@Test
	void getInspectionVisits_shouldApplyDateRangeInclusively() {
		when(inspectionVisitStore.findAll()).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 0, 0), InspectionStatus.DRAFT, null),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 1, 0, 0), InspectionStatus.DRAFT, null)));
		when(viewedPropertyStore.findSummariesByVisitIds(anyList())).thenReturn(List.of());
		noExtras();

		List<InspectionVisitRdo> result = inspectionVisitQueryFlow.getInspectionVisits(null, null, null, null,
			LocalDateTime.of(2026, 9, 17, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0));

		assertThat(result).extracting(InspectionVisitRdo::getInspectionVisitId).containsExactly("v1");
	}

	@Test
	void getInspectionVisits_shouldRequireAllRequestedTags() {
		when(inspectionVisitStore.findAll()).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0), InspectionStatus.DRAFT, null),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 10, 10, 0), InspectionStatus.DRAFT, null)));
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList()))
			.thenReturn(Map.of("v1", List.of("한강", "직주근접"), "v2", List.of("한강")));
		when(viewedPropertyStore.findSummariesByVisitIds(anyList())).thenReturn(List.of());
		when(inspectionAreaStore.findAllByIds(anySet()))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());

		List<InspectionVisitRdo> result = inspectionVisitQueryFlow.getInspectionVisits(null, null, null,
			List.of("한강", "직주근접"), null, null);

		assertThat(result).extracting(InspectionVisitRdo::getInspectionVisitId).containsExactly("v1");
	}

	@Test
	void getInspectionVisits_shouldCarryComplexNameOnTopInterestProperty() {
		when(inspectionVisitStore.findAll()).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0), InspectionStatus.DRAFT, null)));
		ViewedPropertySummaryPdo property = pdo("p1", "v1", 5, 1);
		when(viewedPropertyStore.findSummariesByVisitIds(anyList())).thenReturn(List.of(property));
		noExtras();

		InspectionVisitRdo rdo = inspectionVisitQueryFlow.getInspectionVisits(null, null, null, null, null, null)
			.get(0);

		assertThat(rdo.getPropertyCount()).isEqualTo(1);
		assertThat(rdo.getTopInterestProperty().getComplexName()).isEqualTo("트리마제");
	}

	@Test
	void getInspectionVisits_shouldReturnEmptyWithoutFurtherQueriesWhenNothingMatches() {
		when(inspectionVisitStore.findAll()).thenReturn(List.of());

		assertThat(inspectionVisitQueryFlow.getInspectionVisits(null, null, null, null, null, null)).isEmpty();
		verifyNoInteractions(viewedPropertyStore, fileBoxStore);
	}
}
