package com.seoulchonnom.aggregate.flow.inspection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.filebox.store.FileBoxStore;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionFileException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionOrderException;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.filebox.mapper.FileBoxMapper;

import lombok.RequiredArgsConstructor;

/**
 * 한 임장 기록의 FileBox 하나가 임장 사진과 그 임장에 속한 매물 사진을 함께 담는다.
 *
 * 임장과 매물이 서로 다른 화면에서 편집되므로 동기화는 "대상 그룹 단위 치환"으로 동작한다.
 * 임장 저장은 targetType=INSPECTION_VISIT 항목만, 매물 저장은 자기 targetId 항목만 갈아끼운다.
 */
@Component
@RequiredArgsConstructor
public class InspectionPhotoSupport {
	private final FileBoxStore fileBoxStore;
	private final FileAssetStore fileAssetStore;
	private final FileBoxMapper fileBoxMapper;

	/**
	 * 임장 사진 그룹만 치환한다. files가 null이면 기존 연결을 그대로 둔다.
	 */
	public void syncVisitPhotos(String visitId, List<FileBoxItemUdo> files) {
		if (files == null) {
			return;
		}
		replaceGroup(visitId, FileBoxTargetType.INSPECTION_VISIT, null, files);
	}

	/**
	 * 이 매물의 사진 그룹만 치환한다. 다른 매물과 임장 사진은 건드리지 않는다.
	 */
	public void syncPropertyPhotos(String visitId, String propertyId, List<FileBoxItemUdo> files) {
		if (files == null) {
			return;
		}
		replaceGroup(visitId, FileBoxTargetType.VIEWED_PROPERTY, propertyId, files);
	}

	/**
	 * 매물 삭제 시 그 매물의 항목만 걷어낸다.
	 */
	public void removePropertyPhotos(String visitId, String propertyId) {
		List<FileBoxItem> remaining = existingItems(visitId).stream()
			.filter(item -> !isSameGroup(item, FileBoxTargetType.VIEWED_PROPERTY, propertyId))
			.toList();
		fileBoxStore.syncItems(FileBoxOwnerType.INSPECTION_VISIT, visitId, new ArrayList<>(remaining));
	}

	/**
	 * 사진 sortOrder만 갱신한다. 요청에 빠진 항목은 기존 순서를 유지한다.
	 *
	 * files 배열 전체 치환으로 순서를 바꾸면 사진 30장짜리 임장에서 순서 하나 바꾸는 데
	 * 배열 전체를 되돌려보내야 한다. targetType을 요청에서 받지 않고 서버가 항목을 찾아 판정한다.
	 */
	public void applyItemOrder(String visitId, List<FileBoxItemOrderUdo> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		Map<String, Integer> requested = new HashMap<>();
		for (FileBoxItemOrderUdo order : orders) {
			if (!StringUtils.hasText(order.getItemId())) {
				throw new InvalidInspectionOrderException("정렬 대상 사진 ID가 비어 있습니다.");
			}
			requested.put(order.getItemId(), order.getSortOrder());
		}

		List<FileBoxItem> items = existingItems(visitId);
		Set<String> knownIds = new HashSet<>();
		items.forEach(item -> knownIds.add(item.getId()));
		if (!knownIds.containsAll(requested.keySet())) {
			throw new InvalidInspectionOrderException("이 임장에 속하지 않은 사진이 정렬 요청에 포함되었습니다.");
		}

		items.forEach(item -> {
			Integer sortOrder = requested.get(item.getId());
			if (sortOrder != null) {
				item.setSortOrder(sortOrder);
			}
		});
		fileBoxStore.syncItems(FileBoxOwnerType.INSPECTION_VISIT, visitId, items);
	}

	public void deleteAll(String visitId) {
		fileBoxStore.deleteByOwner(FileBoxOwnerType.INSPECTION_VISIT, visitId);
	}

	public List<FileBoxItem> findItems(String visitId) {
		return existingItems(visitId);
	}

	/**
	 * FileAsset을 매 항목마다 조회하므로 상세 화면에서만 쓴다.
	 */
	public List<FileBoxItemRdo> toRdos(List<FileBoxItem> items) {
		return items.stream()
			.sorted(Comparator.comparing(FileBoxItem::getTargetType)
				.thenComparing(item -> item.getTargetId() == null ? "" : item.getTargetId())
				.thenComparing(FileBoxItem::getRole)
				.thenComparingInt(FileBoxItem::getSortOrder)
				.thenComparing(FileBoxItem::getId))
			.map(item -> fileBoxMapper.toFileBoxItemRdo(item,
				FileAssetRdo.from(fileAssetStore.findById(item.getFileAssetId()))))
			.toList();
	}

	private void replaceGroup(String visitId, FileBoxTargetType targetType, String targetId,
		List<FileBoxItemUdo> files) {
		List<FileBoxItem> existing = existingItems(visitId);
		Map<String, Integer> existingSortOrders = new HashMap<>();
		Set<String> existingIds = new HashSet<>();
		for (FileBoxItem item : existing) {
			if (StringUtils.hasText(item.getId())) {
				existingIds.add(item.getId());
				existingSortOrders.put(item.getId(), item.getSortOrder());
			}
		}

		List<FileBoxItem> replacement = files.stream()
			.map(fileBoxMapper::toFileBoxItem)
			.toList();
		for (FileBoxItem item : replacement) {
			// targetType/targetId는 경로에서 확정한다. FE가 잘못 보낼 여지를 없앤다
			item.setTargetType(targetType);
			item.setTargetId(targetId);
			if (StringUtils.hasText(item.getId()) && !existingIds.contains(item.getId())) {
				throw new InvalidInspectionFileException("이 임장의 항목이 아닌 사진 id가 포함되었습니다.");
			}
		}
		validate(replacement, existingSortOrders);

		List<FileBoxItem> merged = new ArrayList<>(existing.stream()
			.filter(item -> !isSameGroup(item, targetType, targetId))
			.toList());
		merged.addAll(replacement);
		fileBoxStore.syncItems(FileBoxOwnerType.INSPECTION_VISIT, visitId, merged);
	}

	/**
	 * 기존 id를 가진 항목은 기존 sortOrder를 유지하고, 신규 항목만 그룹 말미에 채번한다.
	 * 이 단서가 없으면 정렬 전용 엔드포인트로 방금 맞춘 순서를, 같은 화면의 저장 버튼이
	 * 캐시된 files 배열을 되보내면서 조용히 덮어쓴다.
	 */
	private void validate(List<FileBoxItem> items, Map<String, Integer> existingSortOrders) {
		Set<String> duplicateKeys = new HashSet<>();
		Map<String, Integer> groupMaxOrder = new HashMap<>();
		int coverCount = 0;

		for (FileBoxItem item : items) {
			if (item == null || !StringUtils.hasText(item.getFileAssetId()) || item.getRole() == null) {
				throw new InvalidInspectionFileException("사진 연결 정보가 올바르지 않습니다.");
			}
			item.setFileAssetId(item.getFileAssetId().trim());
			// RAW 첨부는 여행 앨범에만 둔다. 임장은 현장 기록이라 원본 보관이 목적이 아니다.
			if (StringUtils.hasText(item.getRawFileAssetId())) {
				throw new InvalidInspectionFileException("임장 사진에는 RAW 파일을 첨부할 수 없습니다.");
			}
			if (FileBoxItemRole.COVER != item.getRole() && FileBoxItemRole.GALLERY != item.getRole()) {
				throw new InvalidInspectionFileException("임장 사진 role은 COVER 또는 GALLERY만 허용합니다.");
			}
			if (FileBoxItemRole.COVER == item.getRole()) {
				coverCount++;
			}
			String duplicateKey = item.getTargetType() + "|" + item.getTargetId() + "|" + item.getRole() + "|"
				+ item.getFileAssetId();
			if (!duplicateKeys.add(duplicateKey)) {
				throw new InvalidInspectionFileException("이미 연결된 사진입니다.");
			}
			FileAsset fileAsset = fileAssetStore.findById(item.getFileAssetId());
			if (!FileType.INSPECTION.equals(fileAsset.getType())) {
				throw new InvalidInspectionFileException("임장 사진의 파일 타입은 inspection이어야 합니다.");
			}
			Integer keptOrder = existingSortOrders.get(item.getId());
			if (keptOrder != null) {
				item.setSortOrder(keptOrder);
			}
			String groupKey = item.getTargetType() + "|" + item.getTargetId() + "|" + item.getRole();
			groupMaxOrder.merge(groupKey, item.getSortOrder(), Math::max);
		}

		// COVER는 대상별 0 또는 1건. 사진 없는 임장 기록이 가능해야 하므로 필수로 두지 않는다
		if (coverCount > 1) {
			throw new InvalidInspectionFileException("대표 사진은 1개까지만 지정할 수 있습니다.");
		}

		for (FileBoxItem item : items) {
			if (item.getSortOrder() > 0) {
				continue;
			}
			String groupKey = item.getTargetType() + "|" + item.getTargetId() + "|" + item.getRole();
			int nextOrder = groupMaxOrder.getOrDefault(groupKey, 0) + 1;
			item.setSortOrder(nextOrder);
			groupMaxOrder.put(groupKey, nextOrder);
		}
	}

	private List<FileBoxItem> existingItems(String visitId) {
		return fileBoxStore.findOptionalByOwner(FileBoxOwnerType.INSPECTION_VISIT, visitId)
			.map(FileBox::getItems)
			.map(ArrayList::new)
			.map(items -> (List<FileBoxItem>)items)
			.orElseGet(ArrayList::new);
	}

	private boolean isSameGroup(FileBoxItem item, FileBoxTargetType targetType, String targetId) {
		return item.getTargetType() == targetType && Objects.equals(item.getTargetId(), targetId);
	}
}
