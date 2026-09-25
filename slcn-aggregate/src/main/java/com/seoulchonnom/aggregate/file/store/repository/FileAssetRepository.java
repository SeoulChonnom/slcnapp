package com.seoulchonnom.aggregate.file.store.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.file.store.doc.FileAssetDoc;
import com.seoulchonnom.spec.file.entity.vo.FileKind;
import com.seoulchonnom.spec.file.entity.vo.FileStatus;

@Repository
public interface FileAssetRepository extends MongoRepository<FileAssetDoc, String> {
	List<FileAssetDoc> findByKindAndStatusAndRegisteredTimeLessThan(FileKind kind, FileStatus status,
		long registeredTime);
}
