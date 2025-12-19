package com.seoulchonnom.aggregate.user.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;

@Repository
public interface UserRepository extends JpaRepository<UserJpo, String> {
	Optional<UserJpo> findUserJpoById(String id);
}
