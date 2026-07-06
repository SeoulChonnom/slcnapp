package com.seoulchonnom.aggregate.filebox.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.filebox.store.doc.FileBoxDoc;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxOwnerType;

@Repository
public interface FileBoxRepository extends MongoRepository<FileBoxDoc, String> {
	Optional<FileBoxDoc> findByOwnerTypeAndOwnerId(FileBoxOwnerType ownerType, String ownerId);

	List<FileBoxDoc> findAllByOwnerTypeAndOwnerIdIn(FileBoxOwnerType ownerType, Collection<String> ownerIds);

	void deleteByOwnerTypeAndOwnerId(FileBoxOwnerType ownerType, String ownerId);
}
