package com.seoulchonnom.aggregate.file.store.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.file.store.doc.FileAssetDoc;

@Repository
public interface FileAssetRepository extends MongoRepository<FileAssetDoc, String> {
}
