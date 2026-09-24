package com.seoulchonnom.aggregate.flow.inspection;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.util.PageRequestSupport;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.spec.common.response.PageRdo;
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
	 * visitedAt 내림차순, 동점은 id 오름차순. 모든 필터는 선택이며 null/빈 값이면 적용하지 않는다.
	 * areaId/status/revisitIntent/from/to/tag(AND) 전부 DB 조건으로 내리고 페이징도 DB에서 한다
	 * (InspectionVisitRepository.findFiltered 참고) — 예전처럼 전건을 읽어 자바에서 거르면
	 * LIMIT과 메모리 필터가 겹쳐 페이지 크기가 들쭉날쭉해지고 hasNext가 깨진다.
	 *
	 * 필터링된 현재 페이지 안에서는 저장소 왕복이 7회로 고정된다: 임장 1(findFiltered) +
	 * 총건수 1(countFiltered) + 태그 2(연결+마스터) + 지역 1 + 매물 요약 1 + FileBox 1
	 * + FileAsset 1. **페이지 크기에는 비례하지만 전체 임장 건수에는 비례하지 않는 것이 요점이다.**
	 */
	public PageRdo<InspectionVisitRdo> getInspectionVisits(String areaId, InspectionStatus status,
		RevisitIntent revisitIntent, List<String> tags, LocalDateTime from, LocalDateTime to, int page, int size) {
		int normalizedPage = PageRequestSupport.normalizePage(page);
		int normalizedSize = PageRequestSupport.normalizeSize(size);
		int offset = PageRequestSupport.offsetOf(normalizedPage, normalizedSize);

		List<InspectionVisit> visits = inspectionVisitStore.findFiltered(areaId, status, revisitIntent, from, to,
			tags, normalizedSize, offset);
		long totalCount = inspectionVisitStore.countFiltered(areaId, status, revisitIntent, from, to, tags);
		boolean hasNext = (long)(normalizedPage + 1) * normalizedSize < totalCount;

		if (visits.isEmpty()) {
			return new PageRdo<>(List.of(), totalCount, hasNext);
		}

		List<String> visitIds = visits.stream().map(InspectionVisit::getId).toList();
		Map<String, List<String>> tagNames = inspectionTagStore.findVisitTagNamesByVisitIds(visitIds);
		Map<String, InspectionArea> areas = areaMap(visits);
		Map<String, List<ViewedPropertySummaryPdo>> properties = propertiesByVisitId(visitIds);
		Map<String, FileBoxItemRdo> covers = visitCovers(visitIds);
		// 회차 안에서만 고르므로 이 맵의 값은 항상 그 자신의 visitedAt 하나뿐이라 동점 규칙이 결과를 바꾸지 않는다
		Map<String, LocalDateTime> visitedAtByVisitId = visits.stream()
			.collect(Collectors.toMap(InspectionVisit::getId, InspectionVisit::getVisitedAt));

		List<ViewedPropertySummaryPdo> emptyProperties = List.of();
		List<InspectionVisitRdo> items = visits.stream()
			.map(visit -> {
				List<ViewedPropertySummaryPdo> visitProperties = properties.getOrDefault(visit.getId(),
					emptyProperties);
				return inspectionVisitMapper.toInspectionVisitRdo(visit, areas.get(visit.getAreaId()),
					visitProperties.size(), toBriefRdo(topInterestProperty(visitProperties, visitedAtByVisitId)),
					tagNames.getOrDefault(visit.getId(), List.of()), covers.get(visit.getId()),
					inspectionSummarySupport.ofVisitCountsOnly(visit, visitProperties));
			})
			.toList();
		return new PageRdo<>(items, totalCount, hasNext);
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
		String visitedAtText = toText(visit.getVisitedAt());

		// prev/next는 이미 로드한 properties 리스트(정렬 순서 그대로)의 앞/뒤 1건이다.
		// area도 이미 로드했으므로 문맥 필드를 채우는 데 저장소 왕복이 늘지 않는다.
		List<ViewedPropertyDetailRdo> propertyRdos = IntStream.range(0, properties.size())
			.mapToObj(index -> {
				ViewedProperty property = properties.get(index);
				return viewedPropertyMapper.toViewedPropertyDetailRdo(property,
					propertyTags.getOrDefault(property.getId(), List.of()), files, questions,
					inspectionSummarySupport.ofProperty(property), area.getId(), area.getName(), visitedAtText,
					briefRdoAt(properties, index - 1), briefRdoAt(properties, index + 1));
			})
			.toList();

		return inspectionVisitMapper.toInspectionVisitDetailRdo(visit, area, visitTags, propertyRdos, files,
			inspectionSummarySupport.ofVisitWithProperties(visit, properties));
	}

	public ViewedPropertyDetailRdo getViewedProperty(String visitId, String propertyId) {
		InspectionVisit visit = inspectionVisitStore.findById(visitId);
		List<ViewedProperty> siblings = viewedPropertyStore.findAllByVisitId(visitId);
		int index = indexOfProperty(siblings, propertyId);
		if (index < 0) {
			throw new ViewedPropertyNotFoundException(
				"이 임장에 속한 매물이 아닙니다. propertyId=" + propertyId);
		}
		InspectionArea area = inspectionAreaStore.findById(visit.getAreaId());
		return buildPropertyDetail(siblings, index, visit, area);
	}

	/**
	 * visitId 없이 매물 단건을 조회한다. A-① 요구사항: FE 라우트가 /property/:propertyId로
	 * 확정되어 링크 직행·새로고침에서 visitId를 알 수 없다. 임장/지역에 소유자 필드가 없어
	 * (멀티테넌시 없음) 별도 소유 검증은 필요 없다.
	 */
	public ViewedPropertyDetailRdo getInspectionProperty(String propertyId) {
		ViewedProperty property = viewedPropertyStore.findById(propertyId);
		InspectionVisit visit = inspectionVisitStore.findById(property.getInspectionVisitId());
		InspectionArea area = inspectionAreaStore.findById(visit.getAreaId());
		List<ViewedProperty> siblings = viewedPropertyStore.findAllByVisitId(visit.getId());
		int index = indexOfProperty(siblings, propertyId);
		return buildPropertyDetail(siblings, index, visit, area);
	}

	/**
	 * 매물 단건 조립 공용 로직. visitId로 들어오든 propertyId만으로 들어오든 문맥
	 * (지역/일시/이전·다음 매물)까지 같은 방식으로 채운다.
	 */
	private ViewedPropertyDetailRdo buildPropertyDetail(List<ViewedProperty> siblings, int index,
		InspectionVisit visit, InspectionArea area) {
		ViewedProperty property = siblings.get(index);
		Map<String, InspectionQuestion> questions = questionMapOf(List.of(property));
		Map<String, List<String>> propertyTags = inspectionTagStore.findPropertyTagNamesByPropertyIds(
			List.of(property.getId()));

		return viewedPropertyMapper.toViewedPropertyDetailRdo(property,
			propertyTags.getOrDefault(property.getId(), List.of()), fileItems(visit.getId()), questions,
			inspectionSummarySupport.ofProperty(property), area.getId(), area.getName(), toText(visit.getVisitedAt()),
			briefRdoAt(siblings, index - 1), briefRdoAt(siblings, index + 1));
	}

	private int indexOfProperty(List<ViewedProperty> properties, String propertyId) {
		for (int i = 0; i < properties.size(); i++) {
			if (properties.get(i).getId().equals(propertyId)) {
				return i;
			}
		}
		return -1;
	}

	private ViewedPropertyBriefRdo briefRdoAt(List<ViewedProperty> properties, int index) {
		if (index < 0 || index >= properties.size()) {
			return null;
		}
		return viewedPropertyMapper.toViewedPropertyBriefRdo(properties.get(index));
	}

	private String toText(LocalDateTime value) {
		return value == null ? null : value.toString();
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
	 * interestLevel 내림차순 → visitedAt 내림차순(동점이면 최신 회차 우선) → sortOrder 오름차순.
	 * null interestLevel은 최하위다.
	 *
	 * visitedAt은 ViewedPropertySummaryPdo에 없고 inspectionVisitId만 있어 호출자가
	 * 이미 손에 든 List<InspectionVisit>에서 만든 맵으로 받는다 — 추가 저장소 조회를 만들지 않는다.
	 * 회차 안에서만 고르는 호출(임장 목록의 topInterestProperty)은 모든 매물의 visitedAt이
	 * 같아 이 동점 규칙이 결과를 바꾸지 않는다.
	 */
	public ViewedPropertySummaryPdo topInterestProperty(List<ViewedPropertySummaryPdo> properties,
		Map<String, LocalDateTime> visitedAtByVisitId) {
		return properties.stream()
			.max(Comparator
				.comparingInt((ViewedPropertySummaryPdo property) ->
					property.getInterestLevel() == null ? Integer.MIN_VALUE : property.getInterestLevel())
				.thenComparing(
					(ViewedPropertySummaryPdo property) -> visitedAtByVisitId.get(property.getInspectionVisitId()),
					Comparator.nullsFirst(Comparator.naturalOrder()))
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
