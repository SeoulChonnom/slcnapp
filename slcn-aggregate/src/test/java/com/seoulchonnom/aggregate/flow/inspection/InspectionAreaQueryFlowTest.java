package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.MatchedPropertyPdo;
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
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.AreaViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;
import com.seoulchonnom.spec.inspection.mapper.InspectionVisitMapper;

class InspectionAreaQueryFlowTest {
	private final InspectionAreaStore inspectionAreaStore = mock(InspectionAreaStore.class);
	private final InspectionVisitStore inspectionVisitStore = mock(InspectionVisitStore.class);
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final ViewedPropertyStore viewedPropertyStore = mock(ViewedPropertyStore.class);
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow = mock(InspectionVisitQueryFlow.class);
	private final FileBoxStore fileBoxStore = mock(FileBoxStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final InspectionAreaQueryFlow inspectionAreaQueryFlow = new InspectionAreaQueryFlow(inspectionAreaStore,
		inspectionVisitStore, inspectionTagStore, viewedPropertyStore, inspectionVisitQueryFlow, fileBoxStore,
		fileAssetStore, new FileBoxMapper(), new InspectionAreaMapper(), new InspectionVisitMapper(),
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

	private static ViewedPropertySummaryPdo pdo(String id, String visitId, String complexName, int sortOrder) {
		ViewedPropertySummaryPdo pdo = mock(ViewedPropertySummaryPdo.class);
		when(pdo.getId()).thenReturn(id);
		when(pdo.getInspectionVisitId()).thenReturn(visitId);
		when(pdo.getComplexName()).thenReturn(complexName);
		when(pdo.getName()).thenReturn("101동 " + id);
		when(pdo.getInterestLevel()).thenReturn(5);
		when(pdo.getStatus()).thenReturn(InspectionStatus.DRAFT);
		when(pdo.getSortOrder()).thenReturn(sortOrder);
		return pdo;
	}

	private void noProperties() {
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList())).thenReturn(Map.of());
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());
	}

	/**
	 * findAllOrderedByName(keyword) 대신 DB가 페이징한 area id 목록(findAreaIdsPage)을 받아
	 * findAllByIds로 되읽는 새 경로를 표준 스텁 하나로 감싼다. 정렬/키워드/revisitIntent
	 * 인자는 이 테스트들의 관심사가 아니므로 any()로 받는다.
	 */
	private void stubAreaPage(List<InspectionArea> areas) {
		List<String> ids = areas.stream().map(InspectionArea::getId).toList();
		when(inspectionAreaStore.findAreaIdsPage(any(), any(), any(), anyInt(), anyInt())).thenReturn(ids);
		when(inspectionAreaStore.findAllByIds(anyList())).thenReturn(areas);
		when(inspectionAreaStore.countMatchingAreas(any(), any())).thenReturn((long)areas.size());
		stubGlobalAggregatesToZero();
	}

	/**
	 * revisitIntentCounts/totals는 필터 무관 전역 값이라 getInspectionAreas를 호출하는 모든
	 * 테스트가 이 네 쿼리를 반드시 거친다. 각 테스트의 관심사가 아니면 0/빈 값으로 무해하게 채운다.
	 */
	private void stubGlobalAggregatesToZero() {
		when(inspectionAreaStore.countAreas()).thenReturn(0L);
		when(inspectionAreaStore.countAreasByLatestRevisitIntent()).thenReturn(Map.of());
		when(inspectionAreaStore.countAllVisits()).thenReturn(0L);
		when(inspectionAreaStore.countAllProperties()).thenReturn(0L);
	}

	private static MatchedPropertyPdo matchedPdo(String id, String areaId, String visitId, Integer interestLevel,
		LocalDateTime visitedAt, int sortOrder) {
		MatchedPropertyPdo pdo = mock(MatchedPropertyPdo.class);
		when(pdo.getId()).thenReturn(id);
		when(pdo.getAreaId()).thenReturn(areaId);
		when(pdo.getVisitId()).thenReturn(visitId);
		when(pdo.getComplexName()).thenReturn("트리마제");
		when(pdo.getName()).thenReturn("101동 " + id);
		when(pdo.getInterestLevel()).thenReturn(interestLevel);
		when(pdo.getVisitedAt()).thenReturn(visitedAt);
		when(pdo.getSortOrder()).thenReturn(sortOrder);
		return pdo;
	}

	@Test
	void getInspectionAreas_shouldKeepQueryCountFixedRegardlessOfAreaCount() {
		stubAreaPage(List.of(
			new InspectionArea("INSPECTION_AREA-0001", "성수동", null),
			new InspectionArea("INSPECTION_AREA-0002", "잠실", null),
			new InspectionArea("INSPECTION_AREA-0003", "마곡", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0002", LocalDateTime.of(2026, 9, 10, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20);

		// 지역이 3건이어도 임장/매물/태그/FileBox 조회는 각각 1회다
		verify(inspectionVisitStore, times(1)).findAllByAreaIds(anyList());
		verify(inspectionVisitQueryFlow, times(1)).propertiesByVisitId(anyList());
		verify(inspectionTagStore, times(1)).findVisitTagNamesByVisitIds(anyList());
		verify(fileBoxStore, times(1)).findAllByOwnerTypeAndOwnerIdIn(any(), anyList());
	}

	@Test
	void getInspectionAreas_shouldAggregateVisitCountAndDateRange() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20).getItems().get(0);

		assertThat(rdo.getVisitCount()).isEqualTo(2);
		assertThat(rdo.getFirstVisitedAt()).isEqualTo("2026-09-03T10:00");
		assertThat(rdo.getLastVisitedAt()).isEqualTo("2026-09-17T14:00");
		assertThat(rdo.getLatestVisit().getVisitId()).isEqualTo("v1");
	}

	@Test
	void getInspectionAreas_shouldReturnDefaultsForAreaWithoutVisit() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20).getItems().get(0);

		assertThat(rdo.getVisitCount()).isZero();
		assertThat(rdo.getLatestVisit()).isNull();
		assertThat(rdo.getTopProperty()).isNull();
		assertThat(rdo.getFirstVisitedAt()).isNull();
		assertThat(rdo.getThumbnails()).isEmpty();
	}

	@Test
	void getInspectionAreas_shouldPickCoverOfLatestVisitFirstAndCapAtTwo() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
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

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20).getItems().get(0);

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
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		ViewedPropertySummaryPdo olderTop = mock(ViewedPropertySummaryPdo.class);
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList()))
			.thenReturn(Map.of("v1", List.of(), "v2", List.of(olderTop)));
		when(inspectionTagStore.findVisitTagNamesByVisitIds(anyList())).thenReturn(Map.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		when(fileAssetStore.findAllByIds(anyList())).thenReturn(List.of());

		inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20);

		// 최신 회차가 아니라 지역 전체에서 고른다
		verify(inspectionVisitQueryFlow).topInterestProperty(eq(List.of(olderTop)), anyMap());
	}

	@Test
	void getInspectionAreas_shouldCountDraftVisitsInSummary() {
		InspectionVisit completed = visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0));
		completed.changeStatus(InspectionStatus.COMPLETED);
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of(completed,
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20).getItems().get(0);

		assertThat(rdo.getIncompleteSummary().getDraftVisitCount()).isEqualTo(1);
	}

	@Test
	void getInspectionAreas_shouldReturnEmptyItemsWithoutAssemblyWhenPageHasNoAreas() {
		when(inspectionAreaStore.findAreaIdsPage(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
		when(inspectionAreaStore.countMatchingAreas(any(), any())).thenReturn(0L);
		stubGlobalAggregatesToZero();

		InspectionAreaListRdo result = inspectionAreaQueryFlow.getInspectionAreas("없는지역", null, null, 0, 20);

		assertThat(result.getItems()).isEmpty();
		verifyNoInteractions(inspectionVisitStore, viewedPropertyStore);
	}

	/**
	 * revisitIntentCounts는 keyword/revisitIntent/page 어느 것에도 영향받지 않는 전역 값이다.
	 * 필터를 걸어도(여기서는 keyword="성수") countAreasByLatestRevisitIntent/countAreas가
	 * 그 필터 파라미터 없이 호출된다는 것으로 이를 검증한다.
	 */
	@Test
	void getInspectionAreas_shouldComputeRevisitIntentCountsIndependentlyOfFilters() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionAreaStore.countAreas()).thenReturn(7L);
		when(inspectionAreaStore.countAreasByLatestRevisitIntent()).thenReturn(Map.of(
			RevisitIntent.YES, 3L, RevisitIntent.MAYBE, 1L, RevisitIntent.NO, 1L));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaListRdo result = inspectionAreaQueryFlow.getInspectionAreas("성수", RevisitIntent.YES, null, 0,
			20);

		// total=7이지만 YES+MAYBE+NO=5다 — 강남처럼 최신 회차 intent가 null이거나 노원처럼
		// 임장이 0건인 지역이 있으면 이 둘은 일치하지 않는다(요구사항의 의도된 차이)
		assertThat(result.getRevisitIntentCounts().getTotal()).isEqualTo(7);
		assertThat(result.getRevisitIntentCounts().getYes()).isEqualTo(3);
		assertThat(result.getRevisitIntentCounts().getMaybe()).isEqualTo(1);
		assertThat(result.getRevisitIntentCounts().getNo()).isEqualTo(1);
		// keyword="성수", revisitIntent=YES로 필터를 걸었지만 이 전역 집계 쿼리는 필터 인자를 받지 않는다
		verify(inspectionAreaStore).countAreas();
		verify(inspectionAreaStore).countAreasByLatestRevisitIntent();
	}

	@Test
	void getInspectionAreas_shouldClampSizeAboveHundred() {
		when(inspectionAreaStore.findAreaIdsPage(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
		when(inspectionAreaStore.countMatchingAreas(any(), any())).thenReturn(0L);
		stubGlobalAggregatesToZero();

		inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 500);

		verify(inspectionAreaStore).findAreaIdsPage(any(), any(), any(), eq(100), eq(0));
	}

	@Test
	void getInspectionAreas_shouldRejectNegativePage() {
		assertThatThrownBy(() -> inspectionAreaQueryFlow.getInspectionAreas(null, null, null, -1, 20))
			.isInstanceOf(BadRequestException.class);
		verifyNoInteractions(inspectionAreaStore);
	}

	/**
	 * matchedProperty 선택 규칙: interestLevel 내림차순 -> visitedAt 내림차순(동점이면 최신
	 * 회차 우선) -> sortOrder 오름차순. InspectionVisitQueryFlow.topInterestProperty와 같은 기준이다.
	 */
	@Test
	void getInspectionAreas_shouldPickMatchedPropertyByInterestThenLatestVisit() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();
		MatchedPropertyPdo older = matchedPdo("p1", "INSPECTION_AREA-0001", "v1", 5,
			LocalDateTime.of(2026, 9, 3, 10, 0), 1);
		MatchedPropertyPdo newer = matchedPdo("p2", "INSPECTION_AREA-0001", "v2", 5,
			LocalDateTime.of(2026, 9, 17, 14, 0), 1);
		when(viewedPropertyStore.findMatchedProperties(anyList(), eq("트리마제"))).thenReturn(List.of(older, newer));

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas("트리마제", null, null, 0, 20)
			.getItems()
			.get(0);

		assertThat(rdo.getMatchedProperty()).isNotNull();
		assertThat(rdo.getMatchedProperty().getPropertyId()).isEqualTo("p2");
		assertThat(rdo.getMatchedProperty().getVisitId()).isEqualTo("v2");
	}

	@Test
	void getInspectionAreas_shouldLeaveMatchedPropertyNullWhenKeywordBlank() {
		stubAreaPage(List.of(new InspectionArea("INSPECTION_AREA-0001", "성수동", null)));
		when(inspectionVisitStore.findAllByAreaIds(anyList())).thenReturn(List.of());
		when(fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(any(), anyList())).thenReturn(List.of());
		noProperties();

		InspectionAreaRdo rdo = inspectionAreaQueryFlow.getInspectionAreas(null, null, null, 0, 20).getItems().get(0);

		assertThat(rdo.getMatchedProperty()).isNull();
		verifyNoInteractions(viewedPropertyStore);
	}

	@Test
	void getAreaProperties_shouldSortByVisitedAtDescThenSortOrderAsc() {
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(List.of(
			visit("v1", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 17, 14, 0)),
			visit("v2", "INSPECTION_AREA-0001", LocalDateTime.of(2026, 9, 3, 10, 0))));
		// pdo(...)가 내부에서 when().thenReturn()으로 각 getter를 스텁하므로, 바깥쪽
		// when(...).thenReturn(...)의 인자로 바로 넣으면 미완료 스텁 상태에서 또 다른
		// mock 메서드를 호출하게 되어 Mockito가 UnfinishedStubbingException을 던진다.
		// 그래서 먼저 완성된 mock을 변수로 만들어 둔다.
		ViewedPropertySummaryPdo p1 = pdo("p1", "v1", "트리마제", 1);
		ViewedPropertySummaryPdo p2 = pdo("p2", "v1", "트리마제", 2);
		ViewedPropertySummaryPdo p3 = pdo("p3", "v2", "갤러리아포레", 1);
		when(inspectionVisitQueryFlow.propertiesByVisitId(anyList()))
			.thenReturn(Map.of("v1", List.of(p2, p1), "v2", List.of(p3)));

		List<AreaViewedPropertyRdo> result = inspectionAreaQueryFlow.getAreaProperties("INSPECTION_AREA-0001");

		// v1(최신)의 sortOrder 오름차순 다음 v2(과거)가 이어진다
		assertThat(result).extracting(AreaViewedPropertyRdo::getPropertyId).containsExactly("p1", "p2", "p3");
		assertThat(result.get(0).getVisitId()).isEqualTo("v1");
		assertThat(result.get(0).getVisitedAt()).isEqualTo(LocalDateTime.of(2026, 9, 17, 14, 0).toString());
	}

	@Test
	void getAreaProperties_shouldReturnEmptyForAreaWithoutAnyVisit() {
		when(inspectionAreaStore.findById("INSPECTION_AREA-0001"))
			.thenReturn(new InspectionArea("INSPECTION_AREA-0001", "성수동", null));
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-0001")).thenReturn(List.of());

		assertThat(inspectionAreaQueryFlow.getAreaProperties("INSPECTION_AREA-0001")).isEmpty();
		verifyNoInteractions(inspectionVisitQueryFlow);
	}

	@Test
	void getAreaProperties_shouldThrowWhenAreaDoesNotExist() {
		when(inspectionAreaStore.findById("INSPECTION_AREA-9999")).thenThrow(new InspectionAreaNotFoundException());
		when(inspectionVisitStore.findAllByAreaId("INSPECTION_AREA-9999")).thenReturn(List.of());

		assertThatThrownBy(() -> inspectionAreaQueryFlow.getAreaProperties("INSPECTION_AREA-9999"))
			.isInstanceOf(InspectionAreaNotFoundException.class);
	}
}
