package com.seoulchonnom.aggregate.file.store;

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
}
