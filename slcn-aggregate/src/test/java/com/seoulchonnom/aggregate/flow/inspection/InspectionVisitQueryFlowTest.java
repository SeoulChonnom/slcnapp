package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
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

	private static ViewedProperty property(String id, String visitId, int sortOrder) {
		ViewedProperty property = new ViewedProperty(visitId, "트리마제", "101동 " + id, sortOrder);
		property.setId(id);
		return property;
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

	private static Map<String, LocalDateTime> visitedAtOf(String visitId, LocalDateTime visitedAt) {
		return Map.of(visitId, visitedAt);
	}

	@Test
	void topInterestProperty_shouldPreferHigherInterestThenLowerSortOrder() {
		ViewedPropertySummaryPdo low = pdo("p1", "v1", 3, 1);
		ViewedPropertySummaryPdo high = pdo("p2", "v1", 5, 9);
		ViewedPropertySummaryPdo tie = pdo("p3", "v1", 5, 2);
		Map<String, LocalDateTime> visitedAtByVisitId = visitedAtOf("v1", LocalDateTime.of(2026, 9, 17, 14, 0));

		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of(low, high, tie), visitedAtByVisitId).getId())
			.isEqualTo("p3");
	}

	@Test
	void topInterestProperty_shouldTreatNullInterestAsLowest() {
		ViewedPropertySummaryPdo none = pdo("p1", "v1", null, 1);
		ViewedPropertySummaryPdo some = pdo("p2", "v1", 1, 2);
		Map<String, LocalDateTime> visitedAtByVisitId = visitedAtOf("v1", LocalDateTime.of(2026, 9, 17, 14, 0));

		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of(none, some), visitedAtByVisitId).getId())
			.isEqualTo("p2");
	}

	@Test
	void topInterestProperty_shouldReturnNullForEmptyVisit() {
		assertThat(inspectionVisitQueryFlow.topInterestProperty(List.of(), Map.of())).isNull();
	}

	@Test
	void topInterestProperty_shouldPreferLatestVisitOnInterestLevelTie() {
		// 두 회차 모두 관심도 5로 동점이다. sortOrder만 보면 순서가 임의였던 버그를 검증한다
		ViewedPropertySummaryPdo older = pdo("p1", "v1", 5, 1);
		ViewedPropertySummaryPdo newer = pdo("p2", "v2", 5, 1);
		Map<String, LocalDateTime> visitedAtByVisitId = Map.of(
			"v1", LocalDateTime.of(2026, 9, 3, 10, 0),
			"v2", LocalDateTime.of(2026, 9, 17, 14, 0));

		ViewedPropertySummaryPdo result = inspectionVisitQueryFlow.topInterestProperty(List.of(older, newer),
			visitedAtByVisitId);

		assertThat(result.getId()).isEqualTo("p2");
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

	private void noPropertyDetailExtras(String visitId) {
		when(inspectionTagStore.findVisitTagNames(visitId)).thenReturn(List.of());
		when(inspectionTagStore.findPropertyTagNamesByVisitId(visitId)).thenReturn(Map.of());
		when(inspectionTagStore.findPropertyTagNamesByPropertyIds(anyList())).thenReturn(Map.of());
		when(fileBoxStore.findOptionalByOwner(any(), anyString())).thenReturn(Optional.empty());
		when(inspectionQuestionLogic.getQuestionMap(anyList())).thenReturn(Map.of());
	}

	@Test
	void getInspectionVisit_shouldLeavePrevNullOnFirstAndNextNullOnLast() {
		InspectionVisit visit = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0),
			InspectionStatus.DRAFT, null);
		List<ViewedProperty> properties = List.of(property("p1", "v1", 1), property("p2", "v1", 2),
			property("p3", "v1", 3));
		when(inspectionVisitStore.findById("v1")).thenReturn(visit);
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(viewedPropertyStore.findAllByVisitId("v1")).thenReturn(properties);
		noPropertyDetailExtras("v1");

		InspectionVisitDetailRdo detail = inspectionVisitQueryFlow.getInspectionVisit("v1");

		assertThat(detail.getProperties().get(0).getPrevProperty()).isNull();
		assertThat(detail.getProperties().get(0).getNextProperty().getPropertyId()).isEqualTo("p2");
		assertThat(detail.getProperties().get(1).getPrevProperty().getPropertyId()).isEqualTo("p1");
		assertThat(detail.getProperties().get(1).getNextProperty().getPropertyId()).isEqualTo("p3");
		assertThat(detail.getProperties().get(2).getNextProperty()).isNull();
		assertThat(detail.getProperties().get(2).getPrevProperty().getPropertyId()).isEqualTo("p2");
		// 문맥 필드도 이미 로드한 area/visit에서 채워진다
		assertThat(detail.getProperties().get(0).getAreaId()).isEqualTo("INSPECTION_AREA-0001");
		assertThat(detail.getProperties().get(0).getAreaName()).isEqualTo("성수동");
		assertThat(detail.getProperties().get(0).getVisitedAt())
			.isEqualTo(LocalDateTime.of(2026, 9, 17, 14, 0).toString());
	}

	@Test
	void getViewedProperty_shouldCarryAreaContextAndNeighbors() {
		InspectionVisit visit = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0),
			InspectionStatus.DRAFT, null);
		List<ViewedProperty> properties = List.of(property("p1", "v1", 1), property("p2", "v1", 2),
			property("p3", "v1", 3));
		when(inspectionVisitStore.findById("v1")).thenReturn(visit);
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(viewedPropertyStore.findAllByVisitId("v1")).thenReturn(properties);
		noPropertyDetailExtras("v1");

		ViewedPropertyDetailRdo detail = inspectionVisitQueryFlow.getViewedProperty("v1", "p2");

		assertThat(detail.getAreaId()).isEqualTo("INSPECTION_AREA-0001");
		assertThat(detail.getAreaName()).isEqualTo("성수동");
		assertThat(detail.getPrevProperty().getPropertyId()).isEqualTo("p1");
		assertThat(detail.getNextProperty().getPropertyId()).isEqualTo("p3");
	}

	@Test
	void getViewedProperty_shouldRejectPropertyOfAnotherVisit() {
		InspectionVisit visit = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0),
			InspectionStatus.DRAFT, null);
		when(inspectionVisitStore.findById("v1")).thenReturn(visit);
		when(viewedPropertyStore.findAllByVisitId("v1")).thenReturn(List.of(property("p1", "v1", 1)));

		assertThatThrownBy(() -> inspectionVisitQueryFlow.getViewedProperty("v1", "p9"))
			.isInstanceOf(ViewedPropertyNotFoundException.class);
	}

	@Test
	void getInspectionProperty_shouldResolveVisitAndAreaWithoutVisitId() {
		ViewedProperty property = property("p2", "v1", 2);
		InspectionVisit visit = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0),
			InspectionStatus.DRAFT, null);
		List<ViewedProperty> siblings = List.of(property("p1", "v1", 1), property, property("p3", "v1", 3));
		when(viewedPropertyStore.findById("p2")).thenReturn(property);
		when(inspectionVisitStore.findById("v1")).thenReturn(visit);
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(viewedPropertyStore.findAllByVisitId("v1")).thenReturn(siblings);
		noPropertyDetailExtras("v1");

		ViewedPropertyDetailRdo detail = inspectionVisitQueryFlow.getInspectionProperty("p2");

		assertThat(detail.getInspectionVisitId()).isEqualTo("v1");
		assertThat(detail.getAreaName()).isEqualTo("성수동");
		assertThat(detail.getPrevProperty().getPropertyId()).isEqualTo("p1");
		assertThat(detail.getNextProperty().getPropertyId()).isEqualTo("p3");
	}
}
