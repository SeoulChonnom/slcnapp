package com.seoulchonnom.aggregate.common.generator.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.common.generator.store.entity.IdSequence;

public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {
	Optional<IdSequence> findByName(String name);
}
