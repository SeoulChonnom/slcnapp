package com.seoulchonnom.aggregate.filebox.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.filebox.store.doc.FileBoxDoc;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;

@Repository
public interface FileBoxRepository extends MongoRepository<FileBoxDoc, String> {
	Optional<FileBoxDoc> findByOwnerTypeAndOwnerId(FileBoxOwnerType ownerType, String ownerId);

	List<FileBoxDoc> findAllByOwnerTypeAndOwnerIdIn(FileBoxOwnerType ownerType, Collection<String> ownerIds);

	void deleteByOwnerTypeAndOwnerId(FileBoxOwnerType ownerType, String ownerId);

	/**
	 * 임베디드 항목 배열에서 RAW 첨부 id를 찾는다. 필드 경로를 직접 적어 파생 쿼리 이름 규칙에 묶이지 않게 한다.
	 */
	@Query(value = "{ 'items.rawFileAssetId': ?0 }", exists = true)
	boolean existsByItemRawFileAssetId(String rawFileAssetId);
}
