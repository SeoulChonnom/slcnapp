package com.seoulchonnom.aggregate.user.store.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginDoc;

@Repository
public interface UserLoginRepository extends MongoRepository<UserLoginDoc, String> {
}
