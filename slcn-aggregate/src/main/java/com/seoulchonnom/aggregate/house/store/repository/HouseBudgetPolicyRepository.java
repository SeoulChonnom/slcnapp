package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBudgetPolicyJpo;

@Repository
public interface HouseBudgetPolicyRepository extends JpaRepository<HouseBudgetPolicyJpo, String> {
	Optional<HouseBudgetPolicyJpo> findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);
}
