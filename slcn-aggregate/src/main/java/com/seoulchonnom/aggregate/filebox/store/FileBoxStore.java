package com.seoulchonnom.aggregate.filebox.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.filebox.store.mapper.FileBoxDocMapper;
import com.seoulchonnom.aggregate.filebox.store.repository.FileBoxRepository;
import com.seoulchonnom.spec.filebox.entity.FileBox;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FileBoxStore {
	private final FileBoxRepository fileBoxRepository;
	private final FileBoxDocMapper fileBoxDocMapper;

	public FileBox save(FileBox fileBox) {
		return fileBoxDocMapper.toDomain(fileBoxRepository.save(fileBoxDocMapper.toDoc(fileBox)));
	}

	public FileBox findByOwner(FileBoxOwnerType ownerType, String ownerId) {
		return findOptionalByOwner(ownerType, ownerId)
			.orElseThrow(() -> new BadRequestException("FileBox를 찾을 수 없습니다."));
	}

	public Optional<FileBox> findOptionalByOwner(FileBoxOwnerType ownerType, String ownerId) {
		return fileBoxRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
			.map(fileBoxDocMapper::toDomain);
	}

	public FileBox createForOwner(FileBoxOwnerType ownerType, String ownerId) {
		return findOptionalByOwner(ownerType, ownerId)
			.orElseGet(() -> save(FileBox.builder()
				.ownerType(ownerType)
				.ownerId(ownerId)
				.items(new ArrayList<>())
				.build()));
	}

	public FileBox syncItems(FileBoxOwnerType ownerType, String ownerId, List<FileBoxItem> items) {
		FileBox fileBox = findOptionalByOwner(ownerType, ownerId)
			.orElseGet(() -> FileBox.builder()
				.ownerType(ownerType)
				.ownerId(ownerId)
				.items(new ArrayList<>())
				.build());
		fileBox.setItems(withItemIds(fileBox.getItems(), items));
		fileBox.setModifiedTime(System.currentTimeMillis());
		return save(fileBox);
	}

	public void deleteByOwner(FileBoxOwnerType ownerType, String ownerId) {
		fileBoxRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
	}

	private List<FileBoxItem> withItemIds(List<FileBoxItem> existingItems, List<FileBoxItem> items) {
		if (items == null) {
			return new ArrayList<>();
		}
		Set<String> existingIds = new HashSet<>();
		if (existingItems != null) {
			existingItems.stream()
				.map(FileBoxItem::getId)
				.filter(StringUtils::hasText)
				.forEach(existingIds::add);
		}
		Set<String> requestedIds = new HashSet<>();
		return items.stream()
			.map(item -> withItemId(item, existingIds, requestedIds))
			.toList();
	}

	private FileBoxItem withItemId(FileBoxItem item, Set<String> existingIds, Set<String> requestedIds) {
		if (item.getId() == null || item.getId().isBlank()) {
			item.setId(UUID.randomUUID().toString());
		} else if (!existingIds.contains(item.getId())) {
			throw new BadRequestException("FileBoxItem.id가 기존 owner의 item이 아닙니다.");
		}
		if (!requestedIds.add(item.getId())) {
			throw new BadRequestException("FileBoxItem.id가 중복되었습니다.");
		}
		return item;
	}
}
