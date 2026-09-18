package com.seoulchonnom.aggregate.file.store;

import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.file.exception.FileAssetNotFoundException;
import com.seoulchonnom.aggregate.file.store.mapper.FileAssetDocMapper;
import com.seoulchonnom.aggregate.file.store.repository.FileAssetRepository;
import com.seoulchonnom.spec.file.entity.FileAsset;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FileAssetStore {
	private final FileAssetRepository fileAssetRepository;
	private final FileAssetDocMapper fileAssetDocMapper;

	public FileAsset save(FileAsset fileAsset) {
		return fileAssetDocMapper.toDomain(fileAssetRepository.save(fileAssetDocMapper.toDoc(fileAsset)));
	}

	public FileAsset findById(String fileId) {
		return fileAssetRepository.findById(fileId)
			.map(fileAssetDocMapper::toDomain)
			.orElseThrow(FileAssetNotFoundException::new);
	}

	/**
	 * 목록 화면이 사진마다 findById를 반복 호출하지 않도록 한 번에 읽는다.
	 * 없는 id는 결과에서 빠진다 — 호출자가 없는 사진을 건너뛰면 되는 조회 경로에서만 쓴다.
	 */
	public List<FileAsset> findAllByIds(Collection<String> fileIds) {
		if (fileIds == null || fileIds.isEmpty()) {
			return List.of();
		}
		return StreamSupport.stream(fileAssetRepository.findAllById(fileIds).spliterator(), false)
			.map(fileAssetDocMapper::toDomain)
			.toList();
	}

	/**
	 * 백필 전용 페이지 조회. 자산 전체를 한 번에 메모리에 올리지 않기 위해 나눠 읽는다.
	 */
	public List<FileAsset> findPage(int page, int size) {
		return fileAssetRepository.findAll(PageRequest.of(page, size))
			.map(fileAssetDocMapper::toDomain)
			.getContent();
	}
}
