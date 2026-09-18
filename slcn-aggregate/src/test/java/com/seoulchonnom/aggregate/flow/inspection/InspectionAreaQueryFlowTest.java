package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;
import com.seoulchonnom.spec.inspection.mapper.InspectionVisitMapper;

class InspectionAreaQueryFlowTest {
	private final InspectionAreaStore inspectionAreaStore = mock(InspectionAreaStore.class);
	private final InspectionVisitStore inspectionVisitStore = mock(InspectionVisitStore.class);
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = mock(InspectionVisitQueryFlow.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final InspectionAreaQueryFlow inspectionAreaQueryFlow = new InspectionAreaQueryFlow(inspectionAreaStore,
		inspectionVisitStore, inspectionTagStore, inspectionVisitQueryFlow, fileBoxStore, fileAssetStore,
		new FileBoxMapper(), new InspectionAreaMapper(), new InspectionVisitMapper(),
		new InspectionSummarySupport());

	private static InspectionVisit visit(String id, String areaId, LocalDateTime visitedAt) {
		return new InspectionVisit(id, areaId, visitedAt);
	}

	private static FileBox fileBox(String visitId, FileBoxItem... items) {
		return FileBox.builder()
			.ownerType(FileBoxOwnerType.INSPECTION_VISIT)
			.ownerId(visitId)
			.items(new ArrayList<>(List.of(items)))
			.build();
	}

	private static FileBoxItem item(String id, FileBoxItemRole role, int sortOrder) {
		return FileBoxItem.builder()
			.id(id)
			.targetType(FileBoxTargetType.INSPECTION_VISIT)
			.fileAssetId("file-" + id)
			.role(role)
			.sortOrder(sortOrder)
			.build();
	}

	private void noProperties() {
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList())).thenReturn(Map.of());
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());
	}

	@Test
	void getInspectionAreas_shouldKeepQueryCountFixedRegardlessOfAreaCount() {
		when(inspectionAreaStore.findAllVisible(null)).thenReturn(List.of(
			new InspectionArea("INSPECTION_AREA-0001", "성수동", null),
			new InspectionArea("INSPECTION_AREA-0002", "잠실", null),
			new InspectionArea("INSPECTION_AREA-0003", "마곡", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0002", LocalDateTime.of(2026, 9, 10, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		inspectionAreaQueryFlow.getInspectionAreas(null);

		// 지역이 3건이어도 임장/매물/태그/FileBox 조회는 각각 1회다
		verify(inspectionVisitStore, times(1)).findAllByAreaIds(anyList());
		verify(inspectionVisitQueryFlow, times(1)).propertiesByVisitId(anyList());
		verify(inspectionTagStore, times(1)).findVisitTagNamesByVisitIds(anyList());
		verify(fileBoxStore, times(1)).findAllByOwnerTypeAndOwnerIdIn(any(), anyList());
	}

	@Test
	void getInspectionAreas_shouldAggregateVisitCountAndDateRange() {
		when(inspectionAreaStore.findAllVisible(null))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null).get(0);

		assertThat(rdo.getVisitCount()).isEqualTo(2);
		assertThat(rdo.getFirstVisitedAt()).isEqualTo("2026-09-03T10:00");
		assertThat(rdo.getLastVisitedAt()).isEqualTo("2026-09-17T14:00");
		assertThat(rdo.getLatestVisit().getVisitId()).isEqualTo("v1");
	}

	@Test
	void getInspectionAreas_shouldReturnDefaultsForAreaWithoutVisit() {
		when(inspectionAreaStore.findAllVisible(null))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null).get(0);

		assertThat(rdo.getVisitCount()).isZero();
		assertThat(rdo.getLatestVisit()).isNull();
		assertThat(rdo.getTopProperty()).isNull();
		assertThat(rdo.getFirstVisitedAt()).isNull();
		assertThat(rdo.getThumbnails()).isEmpty();
	}

	@Test
	void getInspectionAreas_shouldPickCoverOfLatestVisitFirstAndCapAtTwo() {
		when(inspectionAreaStore.findAllVisible(null))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of(
			fileBox("v1", item("a", FileBoxItemRole.GALLERY, 2), item("b", FileBoxItemRole.COVER, 9),
				item("c", FileBoxItemRole.GALLERY, 1)),
			fileBox("v2", item("d", FileBoxItemRole.COVER, 1))));
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList())).thenReturn(Map.of());
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		FileAsset asset = new FileAsset();
		asset.setId("file-b");
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of(asset));

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null).get(0);

		assertThat(rdo.getThumbnails()).hasSize(2);
		assertThat(rdo.getThumbnails().get(0).getId()).isEqualTo("b");
		assertThat(rdo.getThumbnails().get(1).getId()).isEqualTo("c");
		assertThat(rdo.getTotalImageCount()).isEqualTo(4);
	}

	@Test
	void getInspectionArea_shouldExpandLatestVisitWhenVisitIdOmitted() {
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(inspectionVisitQueryFlow.toVisitSummaries(anyList(), anyMap(), anyMap())).thenReturn(List.of());
		noProperties();

		inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", null, true);

		verify(inspectionVisitQueryFlow).getInspectionVisit("v1");
	}

	@Test
	void getInspectionArea_shouldSkipSelectedVisitWhenPropertiesExcluded() {
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(inspectionVisitQueryFlow.toVisitSummaries(anyList(), anyMap(), anyMap())).thenReturn(List.of());
		noProperties();

		InspectionAreaDetailRdo rdo = inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", null, false);

		assertThat(rdo.getSelectedVisit()).isNull();
		verify(inspectionVisitQueryFlow, never()).getInspectionVisit(anyString());
	}

	@Test
	void getInspectionArea_shouldTruncateVisitsAndFlagHasMore() {
		List<InspectionVisit> visits = new ArrayList<>();
		for (int i = 0; i < InspectionAreaQueryFlow.MAX_VISIT_SUMMARY_COUNT + 3; i++) {
			visits.add(visit("v" + i, "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0).minusDays(i)));
		}
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(visits);
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(inspectionVisitQueryFlow.toVisitSummaries(anyList(), anyMap(), anyMap())).thenReturn(List.of());
		noProperties();

		InspectionAreaDetailRdo rdo = inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", null, false);

		assertThat(rdo.isHasMoreVisits()).isTrue();
		assertThat(rdo.getArea().getVisitCount()).isEqualTo(visits.size());
	}

	@Test
	void getInspectionArea_shouldRejectVisitOfAnotherArea() {
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(inspectionVisitQueryFlow.toVisitSummaries(anyList(), anyMap(), anyMap())).thenReturn(List.of());
		noProperties();

		assertThatThrownBy(() -> inspectionAreaQueryFlow.getInspectionArea("INSPECTION_AREA-0001", "v9", true))
			.isInstanceOf(com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException.class);
	}

	@Test
	void getInspectionAreas_shouldPickTopPropertyAcrossTheWholeArea() {
		when(inspectionAreaStore.findAllVisible(null))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		ViewedPropertySummaryPdo olderTop = mock(ViewedPropertySummaryPdo.class);
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList()))
			.thenReturn(Map.of("v1", List.of(), "v2", List.of(olderTop)));
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());

		inspectionAreaQueryFlow.getInspectionAreas(null);

		// 최신 회차가 아니라 지역 전체에서 고른다
		verify(inspectionVisitQueryFlow).topInterestProperty(List.of(olderTop));
	}

	@Test
	void getInspectionAreas_shouldCountDraftVisitsInSummary() {
		InspectionVisit completed = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0));
		completed.changeStatus(InspectionStatus.COMPLETED);
		when(inspectionAreaStore.findAllVisible(null))
			.thenReturn(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(completed,
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null).get(0);

		assertThat(rdo.getIncompleteSummary().getDraftVisitCount()).isEqualTo(1);
	}
}
