package com.seoulchonnom.aggregate.user.store.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginHistoryDoc;

@Repository
public interface UserLoginHistoryRepository extends MongoRepository<UserLoginHistoryDoc, String> {
}
