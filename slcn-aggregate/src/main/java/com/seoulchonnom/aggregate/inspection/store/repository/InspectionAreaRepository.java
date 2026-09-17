package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionAreaJpo;

@Repository
public interface InspectionAreaRepository extends JpaRepository<InspectionAreaJpo, String> {
	List<InspectionAreaJpo> findAllByHiddenFalseOrderByNameAsc();

	List<InspectionAreaJpo> findAllByHiddenFalseAndNameContainingOrderByNameAsc(String keyword);

	List<InspectionAreaJpo> findAllByIdIn(Collection<String> ids);

	Optional<InspectionAreaJpo> findByName(String name);
}
