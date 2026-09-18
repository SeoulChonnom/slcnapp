package com.seoulchonnom.aggregate.flow.inspection;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
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
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyBriefRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionVisitMapper;
import com.seoulchonnom.spec.inspection.mapper.ViewedPropertyMapper;

import lombok.RequiredArgsConstructor;

/**
 * 임장 목록과 상세를 조립한다.
 *
 * 소박하게 짜면 임장 N건에 대해 지역 N회 + 매물 N회 + 태그 N회 + FileBox N회 = 4N 왕복이 된다.
 * 모든 조회를 배치로 고정해 임장 수와 무관하게 횟수가 일정하다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionVisitQueryFlow {
	private final InspectionVisitStore inspectionVisitStore;
	private final InspectionAreaStore inspectionAreaStore;
	private final ViewedPropertyStore viewedPropertyStore;
	private final InspectionTagStore inspectionTagStore;
	private final InspectionQuestionLogic inspectionQuestionLogic;
	private final FileBoxStore fileBoxStore;
	private final FileAssetStore fileAssetStore;
	private final FileBoxMapper fileBoxMapper;
	private final InspectionVisitMapper inspectionVisitMapper;
	private final ViewedPropertyMapper viewedPropertyMapper;
	private final InspectionSummarySupport inspectionSummarySupport;

	/**
	 * visitedAt 내림차순. 모든 필터는 선택이며 null이면 적용하지 않는다.
	 *
	 * 저장소 왕복 7회로 고정된다: 임장 1 + 태그 2(연결+마스터) + 지역 1 + 매물 요약 1
	 * + FileBox 1 + FileAsset 1. **임장 건수에 비례해 늘지 않는 것이 요점이다.**
	 */
	public List<InspectionVisitRdo> getInspectionVisits(String areaId, InspectionStatus status,
		RevisitIntent revisitIntent, List<String> tags, LocalDateTime from, LocalDateTime to) {
		List<InspectionVisit> visits = filterVisits(areaId, status, revisitIntent, from, to);
		if (visits.isEmpty()) {
			return List.of();
		}

		List<String> visitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, List<String>> tagNames = inspectionTagStore.findVisitTagNamesByVisitIds(visitIds);
		visits = applyTagFilter(visits, tagNames, tags);
		if (visits.isEmpty()) {
			return List.of();
		}

		visitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, InspectionArea> areas = areaMap(visits);
		Map<String, List<ViewedPropertySummaryPdo>> properties = propertiesByVisitId(visitIds);
		Map<String, FileBoxItemRdo> covers = visitCovers(visitIds);

		List<ViewedPropertySummaryPdo> emptyProperties = List.of();
		return visits.stream()
			.map(visit -> {
				List<ViewedPropertySummaryPdo> visitProperties = properties.getOrDefault(visit.getId(),
					emptyProperties);
				return inspectionVisitMapper.toInspectionVisitRdo(visit, areas.get(visit.getAreaId()),
					visitProperties.size(), toBriefRdo(topInterestProperty(visitProperties)),
					tagNames.getOrDefault(visit.getId(), List.of()), covers.get(visit.getId()),
					inspectionSummarySupport.ofVisitCountsOnly(visit, visitProperties));
			})
			.toList();
	}

	/**
	 * 임장 상세. 저장소 왕복 10회로 고정되며 **매물 수와 문답 수에 비례해 늘지 않는다.**
	 *
	 * 내역: 임장 1 + 지역 1 + 매물 1 + 질문 마스터 1 + 임장 태그 2(연결+마스터)
	 * + 매물 태그 2(연결+마스터) + FileBox 1 + FileAsset 1.
	 * 설계 문서 §11.2가 적은 "7회"는 논리 단계를 센 값이라 태그 마스터와 FileAsset 조회가 빠져 있었다.
	 */
	public InspectionVisitDetailRdo getInspectionVisit(String visitId) {
		InspectionVisit visit = inspectionVisitStore.findById(visitId);
		InspectionArea area = inspectionAreaStore.findById(visit.getAreaId());
		List<ViewedProperty> properties = viewedPropertyStore.findAllByVisitId(visitId);

		Map<String, InspectionQuestion> questions = questionMapOf(properties);
		List<String> visitTags = inspectionTagStore.findVisitTagNames(visitId);
		Map<String, List<String>> propertyTags = inspectionTagStore.findPropertyTagNamesByVisitId(visitId);
		List<FileBoxItemRdo> files = fileItems(visitId);

		List<ViewedPropertyDetailRdo> propertyRdos = properties.stream()
			.map(property -> viewedPropertyMapper.toViewedPropertyDetailRdo(property,
				propertyTags.getOrDefault(property.getId(), List.of()), files, questions,
				inspectionSummarySupport.ofProperty(property)))
			.toList();

		return inspectionVisitMapper.toInspectionVisitDetailRdo(visit, area, visitTags, propertyRdos, files,
			inspectionSummarySupport.ofVisitWithProperties(visit, properties));
	}

	public ViewedPropertyDetailRdo getViewedProperty(String visitId, String propertyId) {
		ViewedProperty property = viewedPropertyStore.findById(propertyId);
		if (!visitId.equals(property.getInspectionVisitId())) {
			throw new ViewedPropertyNotFoundException(
				"이 임장에 속한 매물이 아닙니다. propertyId=" + propertyId);
		}
		Map<String, InspectionQuestion> questions = questionMapOf(List.of(property));
		Map<String, List<String>> propertyTags = inspectionTagStore.findPropertyTagNamesByPropertyIds(
			List.of(propertyId));

		return viewedPropertyMapper.toViewedPropertyDetailRdo(property,
			propertyTags.getOrDefault(propertyId, List.of()), fileItems(visitId), questions,
			inspectionSummarySupport.ofProperty(property));
	}

	/**
	 * 지역 상세의 visits[]. 사진을 싣지 않으므로 FileBox 조회가 없다.
	 */
	public List<InspectionVisitSummaryRdo> toVisitSummaries(List<InspectionVisit> visits,
		Map<String, List<ViewedPropertySummaryPdo>> properties, Map<String, List<String>> tagNames) {
		return visits.stream()
			.map(visit -> {
				List<ViewedPropertySummaryPdo> visitProperties = properties.getOrDefault(visit.getId(), List.of());
				return inspectionVisitMapper.toInspectionVisitSummaryRdo(visit,
					tagNames.getOrDefault(visit.getId(), List.of()), visitProperties.size(),
					inspectionSummarySupport.ofVisitCountsOnly(visit, visitProperties), null);
			})
			.toList();
	}

	public Map<String, List<ViewedPropertySummaryPdo>> propertiesByVisitId(List<String> visitIds) {
		return viewedPropertyStore.findSummariesByVisitIds(visitIds).stream()
			.collect(Collectors.groupingBy(ViewedPropertySummaryPdo::getInspectionVisitId));
	}

	/**
	 * interestLevel 내림차순, 동률이면 sortOrder 오름차순. null은 최하위다.
	 */
	public ViewedPropertySummaryPdo topInterestProperty(List<ViewedPropertySummaryPdo> properties) {
		return properties.stream()
			.max(Comparator
				.comparingInt((ViewedPropertySummaryPdo property) ->
					property.getInterestLevel() == null ? Integer.MIN_VALUE : property.getInterestLevel())
				.thenComparing(ViewedPropertySummaryPdo::getSortOrder, Comparator.reverseOrder()))
			.orElse(null);
	}

	public ViewedPropertyBriefRdo toBriefRdo(ViewedPropertySummaryPdo property) {
		if (property == null) {
			return null;
		}
		return new ViewedPropertyBriefRdo(property.getId(), property.getComplexName(), property.getName(),
			property.getInterestLevel());
	}

	private List<InspectionVisit> filterVisits(String areaId, InspectionStatus status, RevisitIntent revisitIntent,
		LocalDateTime from, LocalDateTime to) {
		List<InspectionVisit> visits = StringUtils.hasText(areaId)
			? inspectionVisitStore.findAllByAreaId(areaId)
			: inspectionVisitStore.findAll();
		return visits.stream()
			.filter(visit -> status == null || status == visit.getStatus())
			.filter(visit -> revisitIntent == null || revisitIntent == visit.getRevisitIntent())
			.filter(visit -> from == null || !visit.getVisitedAt().isBefore(from))
			.filter(visit -> to == null || !visit.getVisitedAt().isAfter(to))
			.toList();
	}

	/**
	 * 태그 필터는 AND다. 요청한 태그를 모두 가진 임장만 남긴다.
	 */
	private List<InspectionVisit> applyTagFilter(List<InspectionVisit> visits, Map<String, List<String>> tagNames,
		List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return visits;
		}
		Set<String> required = Set.copyOf(tags);
		return visits.stream()
			.filter(visit -> tagNames.getOrDefault(visit.getId(), List.of()).containsAll(required))
			.toList();
	}

	private Map<String, InspectionArea> areaMap(List<InspectionVisit> visits) {
		Set<String> areaIds = visits.stream().map(InspectionVisit::getAreaId).collect(Collectors.toSet());
		return inspectionAreaStore.findAllByIds(areaIds).stream()
			.collect(Collectors.toMap(InspectionArea::getId, area -> area));
	}

	/**
	 * 질문 마스터는 배지(isCurrentVersion/questionEnabled) 계산에만 쓴다.
	 * 비어 있어도 문답 렌더링은 스냅샷으로 정상 동작해야 한다.
	 */
	private Map<String, InspectionQuestion> questionMapOf(List<ViewedProperty> properties) {
		List<String> questionIds = properties.stream()
			.flatMap(property -> property.getAnswers() == null ? Stream.<PropertyAnswer>empty()
				: property.getAnswers().stream())
			.map(PropertyAnswer::getQuestionId)
			.distinct()
			.toList();
		return inspectionQuestionLogic.getQuestionMap(questionIds);
	}

	private List<FileBoxItemRdo> fileItems(String visitId) {
		List<FileBoxItem> items = fileBoxStore.findOptionalByOwner(FileBoxOwnerType.INSPECTION_VISIT, visitId)
			.map(FileBox::getItems)
			.orElseGet(List::of);
		return toRdos(items);
	}

	private Map<String, FileBoxItemRdo> visitCovers(List<String> visitIds) {
		List<FileBox> fileBoxes = fileBoxStore.findAllByOwnerTypeAndOwnerIdIn(FileBoxOwnerType.INSPECTION_VISIT,
			visitIds);
		Map<String, FileBoxItem> covers = new LinkedHashMap<>();
		for (FileBox fileBox : fileBoxes) {
			fileBox.getItems().stream()
				.filter(item -> FileBoxTargetType.INSPECTION_VISIT == item.getTargetType())
				.filter(item -> FileBoxItemRole.COVER == item.getRole())
				.findFirst()
				.ifPresent(item -> covers.put(fileBox.getOwnerId(), item));
		}
		Map<String, FileAsset> assets = assetMap(covers.values().stream().map(FileBoxItem::getFileAssetId).toList());

		Map<String, FileBoxItemRdo> result = new HashMap<>();
		covers.forEach((ownerId, item) -> result.put(ownerId,
			fileBoxMapper.toFileBoxItemRdo(item, rdoOf(assets, item.getFileAssetId()))));
		return result;
	}

	private List<FileBoxItemRdo> toRdos(List<FileBoxItem> items) {
		Map<String, FileAsset> assets = assetMap(items.stream().map(FileBoxItem::getFileAssetId).toList());
		return items.stream()
			.sorted(Comparator.comparing(FileBoxItem::getTargetType)
				.thenComparing(item -> item.getTargetId() == null ? "" : item.getTargetId())
				.thenComparing(FileBoxItem::getRole)
				.thenComparingInt(FileBoxItem::getSortOrder)
				.thenComparing(FileBoxItem::getId))
			.map(item -> fileBoxMapper.toFileBoxItemRdo(item, rdoOf(assets, item.getFileAssetId())))
			.toList();
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
