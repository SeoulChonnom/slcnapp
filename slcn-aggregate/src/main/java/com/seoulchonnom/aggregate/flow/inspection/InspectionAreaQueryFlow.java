package com.seoulchonnom.aggregate.flow.inspection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.util.PageRequestSupport;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.MatchedPropertyPdo;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionAreaSort;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.AreaViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaTotalsRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.MatchedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.RevisitIntentCountsRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;
import com.seoulchonnom.spec.inspection.mapper.InspectionVisitMapper;

import lombok.RequiredArgsConstructor;

/**
 * 지역 목록과 지역 상세를 조립한다.
 *
 * 지역마다 회차를 끌어오면 지역 수 x 회차 수만큼 매물·태그·FileBox를 읽게 된다.
 * 저장소 왕복 7회로 고정한다: 지역 1 + 임장 1 + 매물 요약 1 + 태그 2(연결+마스터)
 * + FileBox 1 + FileAsset 1. **지역 수에 비례해 늘지 않는 것이 요점이다.**
 *
 * 집계는 전부 저장하지 않고 여기서 계산한다. 임장 등록/삭제와 동기화가 어긋날 여지를 만들지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionAreaQueryFlow {
	static final int MAX_VISIT_SUMMARY_COUNT = 50;
	private static final int MAX_THUMBNAIL_COUNT = 2;

	private final InspectionAreaStore inspectionAreaStore;
	private final InspectionVisitStore inspectionVisitStore;
	private final InspectionTagStore inspectionTagStore;
	private final ViewedPropertyStore viewedPropertyStore;
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow;
	private final FileBoxStore fileBoxStore;
	private final FileAssetStore fileAssetStore;
	private final FileBoxMapper fileBoxMapper;
	private final InspectionAreaMapper inspectionAreaMapper;
	private final InspectionVisitMapper inspectionVisitMapper;
	private final InspectionSummarySupport inspectionSummarySupport;

	/**
	 * 정렬·검색·페이징은 DB가 담당한다(InspectionAreaStore.findAreaIdsPage). 여기서는 그 결과로
	 * 나온 "이번 페이지에 보여줄 지역 id 목록"만 받아 기존 조립 로직(집계 필드 계산)을
	 * 그 지역들에 대해서만 돌린다 — 예전엔 전 지역을 조립했으니 페이징 후에는 오히려 더 가볍다.
	 *
	 * revisitIntentCounts/totals는 필터와 무관한 전역 값이라 페이지 조립과 별개로 매번 계산한다.
	 * COUNT/GROUP BY 쿼리만 쓰고 전건을 메모리로 끌어오지 않는다.
	 */
	public InspectionAreaListRdo getInspectionAreas(String keyword, RevisitIntent revisitIntent,
		InspectionAreaSort sort, int page, int size) {
		int normalizedPage = PageRequestSupport.normalizePage(page);
		int normalizedSize = PageRequestSupport.normalizeSize(size);
		int offset = PageRequestSupport.offsetOf(normalizedPage, normalizedSize);
		InspectionAreaSort resolvedSort = sort == null ? InspectionAreaSort.RECENT_VISIT : sort;

		List<String> pagedAreaIds = inspectionAreaStore.findAreaIdsPage(resolvedSort, keyword, revisitIntent,
			normalizedSize, offset);
		long totalCount = inspectionAreaStore.countMatchingAreas(keyword, revisitIntent);
		boolean hasNext = (long)(normalizedPage + 1) * normalizedSize < totalCount;

		List<InspectionAreaRdo> items = pagedAreaIds.isEmpty() ? List.of()
			: assembleAreaRdos(pagedAreaIds, keyword);

		long visibleAreaCount = inspectionAreaStore.countAreas();
		Map<RevisitIntent, Long> byLatestIntent = inspectionAreaStore.countAreasByLatestRevisitIntent();
		RevisitIntentCountsRdo revisitIntentCounts = new RevisitIntentCountsRdo(visibleAreaCount,
			byLatestIntent.getOrDefault(RevisitIntent.YES, 0L), byLatestIntent.getOrDefault(RevisitIntent.MAYBE, 0L),
			byLatestIntent.getOrDefault(RevisitIntent.NO, 0L));
		InspectionAreaTotalsRdo totals = new InspectionAreaTotalsRdo(visibleAreaCount,
			inspectionAreaStore.countAllVisits(), inspectionAreaStore.countAllProperties());

		return inspectionAreaMapper.toInspectionAreaListRdo(items, totalCount, hasNext, revisitIntentCounts, totals);
	}

	/**
	 * DB가 정한 순서(pagedAreaIds)대로 지역을 조립한다. findAllByIds는 이 순서를 보존하지
	 * 않으므로 반드시 pagedAreaIds 기준으로 다시 정렬해야 한다.
	 */
	private List<InspectionAreaRdo> assembleAreaRdos(List<String> pagedAreaIds, String keyword) {
		Map<String, InspectionArea> areaById = inspectionAreaStore.findAllByIds(pagedAreaIds).stream()
			.collect(Collectors.toMap(InspectionArea::getId, area -> area));
		List<InspectionArea> areas = pagedAreaIds.stream()
			.map(areaById::get)
			.filter(Objects::nonNull)
			.toList();
		if (areas.isEmpty()) {
			return List.of();
		}

		List<String> areaIds = areas.stream().map(InspectionArea::getId).toList();
		List<InspectionVisit> visits = inspectionVisitStore.findAllByAreaIds(areaIds);
		Map<String, List<InspectionVisit>> visitsByArea = visits.stream()
			.collect(Collectors.groupingBy(InspectionVisit::getAreaId));

		List<String> visitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, List<ViewedPropertySummaryPdo>> propertiesByVisit =
			inspectionVisitQueryFlow.propertiesByVisitId(visitIds);
		Map<String, List<String>> tagNames = inspectionTagStore.findVisitTagNamesByVisitIds(latestVisitIds(
			visitsByArea));
		Map<String, FileBox> fileBoxes = fileBoxesOf(visitIds);

		// 썸네일 FileAsset을 지역마다 조회하면 지역 수에 비례하는 왕복이 생긴다.
		// 이번 페이지 지역의 썸네일을 먼저 골라 한 번에 읽는다
		Map<String, List<FileBoxItem>> thumbnailsByArea = new HashMap<>();
		for (InspectionArea area : areas) {
			thumbnailsByArea.put(area.getId(),
				thumbnails(visitsByArea.getOrDefault(area.getId(), List.of()), fileBoxes));
		}
		Map<String, FileAsset> assets = assetMap(thumbnailsByArea.values().stream()
			.flatMap(List::stream)
			.map(FileBoxItem::getFileAssetId)
			.toList());

		Map<String, MatchedPropertyRdo> matchedByArea = matchedPropertiesByArea(areaIds, keyword);

		return areas.stream()
			.map(area -> {
				InspectionAreaRdo rdo = toAreaRdo(area, visitsByArea.getOrDefault(area.getId(), List.of()),
					propertiesByVisit, tagNames, fileBoxes, thumbnailsByArea.get(area.getId()), assets);
				rdo.setMatchedProperty(matchedByArea.get(area.getId()));
				return rdo;
			})
			.toList();
	}

	/**
	 * keyword가 단지명/매물명에 걸린 매물만 후보다. 한 지역에 여러 후보가 있으면
	 * interestLevel 내림차순 -> visitedAt 내림차순(동점이면 최신 회차 우선) -> sortOrder
	 * 오름차순으로 1건을 고른다. InspectionVisitQueryFlow.topInterestProperty와 같은 규칙이다.
	 */
	private Map<String, MatchedPropertyRdo> matchedPropertiesByArea(List<String> areaIds, String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return Map.of();
		}
		List<MatchedPropertyPdo> matches = viewedPropertyStore.findMatchedProperties(areaIds, keyword);
		if (matches.isEmpty()) {
			return Map.of();
		}
		Map<String, List<MatchedPropertyPdo>> byArea = matches.stream()
			.collect(Collectors.groupingBy(MatchedPropertyPdo::getAreaId));
		Map<String, MatchedPropertyRdo> result = new HashMap<>();
		byArea.forEach((areaId, candidates) -> candidates.stream()
			.max(Comparator
				.comparingInt((MatchedPropertyPdo pdo) ->
					pdo.getInterestLevel() == null ? Integer.MIN_VALUE : pdo.getInterestLevel())
				.thenComparing(MatchedPropertyPdo::getVisitedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(MatchedPropertyPdo::getSortOrder, Comparator.reverseOrder()))
			.ifPresent(top -> result.put(areaId, toMatchedPropertyRdo(top))));
		return result;
	}

	private MatchedPropertyRdo toMatchedPropertyRdo(MatchedPropertyPdo pdo) {
		return new MatchedPropertyRdo(pdo.getId(), pdo.getVisitId(), pdo.getComplexName(), pdo.getName(),
			pdo.getInterestLevel());
	}

	/**
	 * 회차 요약 목록과 선택 회차 상세를 한 번에 반환한다.
	 *
	 * @param visitId           null이면 최신 회차를 펼친다
	 * @param includeProperties false면 selectedVisit을 생략한다. 회차가 많은 지역의 지연 로딩용
	 */
	public InspectionAreaDetailRdo getInspectionArea(String areaId, String visitId, boolean includeProperties) {
		InspectionArea area = inspectionAreaStore.findById(areaId);
		List<InspectionVisit> visits = inspectionVisitStore.findAllByAreaId(areaId);

		boolean hasMoreVisits = visits.size() > MAX_VISIT_SUMMARY_COUNT;
		List<InspectionVisit> shown = hasMoreVisits ? visits.subList(0, MAX_VISIT_SUMMARY_COUNT) : visits;

		List<String> shownIds = shown.stream().map(InspectionVisit::getId).toList();
		Map<String, List<ViewedPropertySummaryPdo>> propertiesByVisit =
			inspectionVisitQueryFlow.propertiesByVisitId(shownIds);
		Map<String, List<String>> tagNames = inspectionTagStore.findVisitTagNamesByVisitIds(shownIds);
		List<InspectionVisitSummaryRdo> summaries = inspectionVisitQueryFlow.toVisitSummaries(shown,
			propertiesByVisit, tagNames);

		List<String> allVisitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, List<ViewedPropertySummaryPdo>> allProperties = allVisitIds.equals(shownIds)
			? propertiesByVisit
			: inspectionVisitQueryFlow.propertiesByVisitId(allVisitIds);
		Map<String, FileBox> fileBoxes = fileBoxesOf(allVisitIds);
		List<FileBoxItem> thumbnailItems = thumbnails(visits, fileBoxes);
		InspectionAreaRdo areaRdo = toAreaRdo(area, visits, allProperties, tagNames, fileBoxes, thumbnailItems,
			assetMap(thumbnailItems.stream().map(FileBoxItem::getFileAssetId).toList()));

		String selectedVisitId = resolveSelectedVisitId(visitId, visits);
		return inspectionAreaMapper.toInspectionAreaDetailRdo(areaRdo, summaries, hasMoreVisits,
			includeProperties && selectedVisitId != null
				? inspectionVisitQueryFlow.getInspectionVisit(selectedVisitId)
				: null);
	}

	/**
	 * 지역 전체 매물 경량 목록(A-②). FE가 complexName+name으로 회차 간 매물을 이어 보여주는
	 * 재료다. 그룹핑은 FE가 하므로 여기서는 정렬된 평면 목록만 만든다.
	 *
	 * 저장소 왕복 2회로 유지한다: 임장 목록 1(findAllByAreaId) + 매물 요약 1
	 * (inspectionVisitQueryFlow.propertiesByVisitId가 감싼 findSummariesByVisitIds).
	 * 회차가 하나도 없을 때만 지역 자체의 존재를 추가로 확인한다 — 존재하는 지역은 매물 없이도
	 * 정상이라 빈 배열을 돌려줘야 하고, 없는 지역은 기존 InspectionAreaNotFoundException 규약을
	 * 따라야 하기 때문이다. 이 한 번의 추가 조회는 그 드문 경우에만 든다.
	 */
	public List<AreaViewedPropertyRdo> getAreaProperties(String areaId) {
		List<InspectionVisit> visits = inspectionVisitStore.findAllByAreaId(areaId);
		if (visits.isEmpty()) {
			inspectionAreaStore.findById(areaId);
			return List.of();
		}

		List<String> visitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, List<ViewedPropertySummaryPdo>> propertiesByVisit =
			inspectionVisitQueryFlow.propertiesByVisitId(visitIds);

		// visits는 이미 visitedAt 내림차순이다(findAllByAreaIdOrderByVisitedAtDescIdAsc).
		// 회차 안에서는 sortOrder 오름차순으로만 더 정렬하면 전체 요구 정렬이 완성된다.
		List<AreaViewedPropertyRdo> result = new ArrayList<>();
		for (InspectionVisit visit : visits) {
			propertiesByVisit.getOrDefault(visit.getId(), List.<ViewedPropertySummaryPdo>of()).stream()
				.sorted(Comparator.comparingInt(ViewedPropertySummaryPdo::getSortOrder))
				.forEach(property -> result.add(toAreaViewedPropertyRdo(property, visit)));
		}
		return result;
	}

	private AreaViewedPropertyRdo toAreaViewedPropertyRdo(ViewedPropertySummaryPdo property, InspectionVisit visit) {
		String visitedAt = visit.getVisitedAt() == null ? null : visit.getVisitedAt().toString();
		return new AreaViewedPropertyRdo(property.getId(), visit.getId(), visitedAt, property.getComplexName(),
			property.getName(), property.getInterestLevel(), property.getStatus());
	}

	private String resolveSelectedVisitId(String visitId, List<InspectionVisit> visits) {
		if (StringUtils.hasText(visitId)) {
			return visits.stream()
				.map(InspectionVisit::getId)
				.filter(visitId::equals)
				.findFirst()
				.orElseThrow(() -> new InspectionVisitNotFoundException(
					"이 지역에 속한 임장이 아닙니다. visitId=" + visitId));
		}
		// visitedAt 내림차순이므로 첫 행이 최신 회차다
		return visits.isEmpty() ? null : visits.get(0).getId();
	}

	private InspectionAreaRdo toAreaRdo(InspectionArea area, List<InspectionVisit> visits,
		Map<String, List<ViewedPropertySummaryPdo>> propertiesByVisit, Map<String, List<String>> tagNames,
		Map<String, FileBox> fileBoxes, List<FileBoxItem> thumbnailItems, Map<String, FileAsset> assets) {
		List<ViewedPropertySummaryPdo> allProperties = visits.stream()
			.flatMap(visit -> propertiesByVisit.getOrDefault(visit.getId(), List.<ViewedPropertySummaryPdo>of())
				.stream())
			.toList();

		InspectionVisit latest = visits.stream()
			.max(Comparator.comparing(InspectionVisit::getVisitedAt))
			.orElse(null);
		InspectionVisitSummaryRdo latestSummary = latest == null ? null
			: inspectionVisitMapper.toInspectionVisitSummaryRdo(latest,
				tagNames.getOrDefault(latest.getId(), List.of()), null, null, null);
		// topProperty는 지역 전체에서 고르므로 동점이면 최신 회차가 이기게 visitedAt을 함께 넘긴다
		Map<String, LocalDateTime> visitedAtByVisitId = visits.stream()
			.collect(Collectors.toMap(InspectionVisit::getId, InspectionVisit::getVisitedAt));

		return inspectionAreaMapper.toInspectionAreaRdo(area, visits.size(),
			visits.stream().map(InspectionVisit::getVisitedAt).min(Comparator.naturalOrder()).orElse(null),
			visits.stream().map(InspectionVisit::getVisitedAt).max(Comparator.naturalOrder()).orElse(null),
			allProperties.size(),
			latestSummary,
			inspectionVisitQueryFlow.toBriefRdo(
				inspectionVisitQueryFlow.topInterestProperty(allProperties, visitedAtByVisitId)),
			inspectionSummarySupport.ofArea(visits, allProperties),
			thumbnailItems.stream()
				.map(item -> fileBoxMapper.toFileBoxItemRdo(item, rdoOf(assets, item.getFileAssetId())))
				.toList(),
			totalImageCount(visits, fileBoxes));
	}

	/**
	 * 최신 회차의 COVER를 먼저, 없으면 sortOrder 앞선 GALLERY 순으로 최대 2건.
	 */
	private List<FileBoxItem> thumbnails(List<InspectionVisit> visits, Map<String, FileBox> fileBoxes) {
		List<FileBoxItem> picked = new ArrayList<>();
		List<InspectionVisit> ordered = visits.stream()
			.sorted(Comparator.comparing(InspectionVisit::getVisitedAt).reversed())
			.toList();
		for (InspectionVisit visit : ordered) {
			FileBox fileBox = fileBoxes.get(visit.getId());
			if (fileBox == null) {
				continue;
			}
			fileBox.getItems().stream()
				.filter(item -> FileBoxTargetType.INSPECTION_VISIT == item.getTargetType())
				.sorted(Comparator
					.comparingInt((FileBoxItem item) -> FileBoxItemRole.COVER == item.getRole() ? 0 : 1)
					.thenComparingInt(FileBoxItem::getSortOrder))
				.forEach(item -> {
					if (picked.size() < MAX_THUMBNAIL_COUNT) {
						picked.add(item);
					}
				});
			if (picked.size() >= MAX_THUMBNAIL_COUNT) {
				break;
			}
		}
		return picked;
	}

	/**
	 * 이 필드만 성격이 다르다. 나머지는 PostgreSQL 집계지만 사진 수는 MongoDB 문서를 실제로 읽어야 나온다.
	 * 회차가 300건을 넘으면 FileBox에 itemCount를 비정규화하거나 이 필드를 목록에서 뺀다.
	 */
	private int totalImageCount(List<InspectionVisit> visits, Map<String, FileBox> fileBoxes) {
		return visits.stream()
			.map(visit -> fileBoxes.get(visit.getId()))
			.filter(fileBox -> fileBox != null && fileBox.getItems() != null)
			.mapToInt(fileBox -> fileBox.getItems().size())
			.sum();
	}

	private List<String> latestVisitIds(Map<String, List<InspectionVisit>> visitsByArea) {
		return visitsByArea.values().stream()
			.map(areaVisits -> areaVisits.stream().max(Comparator.comparing(InspectionVisit::getVisitedAt)))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.map(InspectionVisit::getId)
			.toList();
	}

	private Map<String, FileBox> fileBoxesOf(List<String> visitIds) {
		Map<String, FileBox> result = new HashMap<>();
		fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(FileBoxOwnerType.INSPECTION_VISIT, visitIds)
			.forEach(fileBox -> result.put(fileBox.getOwnerId(), fileBox));
		return result;
	}

	private Map<String, FileAsset> assetMap(List<String> fileAssetIds) {
		return fileAssetStore.findAllByIds(fileAssetIds).stream()
			.collect(Collectors.toMap(FileAsset::getId, asset -> asset, (a, b) -> a));
	}

	private FileAssetRdo rdoOf(Map<String, FileAsset> assets, String fileAssetId) {
		FileAsset asset = assets.get(fileAssetId);
		return asset == null ? null : FileAssetRdo.from(asset);
	}
}
