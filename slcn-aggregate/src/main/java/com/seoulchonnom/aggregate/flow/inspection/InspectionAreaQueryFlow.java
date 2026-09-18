package com.seoulchonnom.aggregate.flow.inspection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
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
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitSummaryRdo;
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
	private final InspectionVisitQueryFlow inspectionVisitQueryFlow;
	private final FileBoxStore fileBoxStore;
	private final FileAssetStore fileAssetStore;
	private final FileBoxMapper fileBoxMapper;
	private final InspectionAreaMapper inspectionAreaMapper;
	private final InspectionVisitMapper inspectionVisitMapper;
	private final InspectionSummarySupport inspectionSummarySupport;

	public List<InspectionAreaRdo> getInspectionAreas(String keyword) {
		List<InspectionArea> areas = inspectionAreaStore.findAllVisible(keyword);
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
		// 전 지역의 썸네일을 먼저 골라 한 번에 읽는다
		Map<String, List<FileBoxItem>> thumbnailsByArea = new HashMap<>();
		for (InspectionArea area : areas) {
			thumbnailsByArea.put(area.getId(),
				thumbnails(visitsByArea.getOrDefault(area.getId(), List.of()), fileBoxes));
		}
		Map<String, FileAsset> assets = assetMap(thumbnailsByArea.values().stream()
			.flatMap(List::stream)
			.map(FileBoxItem::getFileAssetId)
			.toList());

		return areas.stream()
			.map(area -> toAreaRdo(area, visitsByArea.getOrDefault(area.getId(), List.of()), propertiesByVisit,
				tagNames, fileBoxes, thumbnailsByArea.get(area.getId()), assets))
			.toList();
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

		return inspectionAreaMapper.toInspectionAreaRdo(area, visits.size(),
			visits.stream().map(InspectionVisit::getVisitedAt).min(Comparator.naturalOrder()).orElse(null),
			visits.stream().map(InspectionVisit::getVisitedAt).max(Comparator.naturalOrder()).orElse(null),
			allProperties.size(),
			latestSummary,
			inspectionVisitQueryFlow.toBriefRdo(inspectionVisitQueryFlow.topInterestProperty(allProperties)),
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
