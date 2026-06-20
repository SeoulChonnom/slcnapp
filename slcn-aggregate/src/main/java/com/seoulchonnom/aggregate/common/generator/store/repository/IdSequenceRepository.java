package com.seoulchonnom.aggregate.common.generator.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.common.generator.store.entity.IdSequence;

import jakarta.persistence.LockModeType;

public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<IdSequence> findByName(String name);
}
