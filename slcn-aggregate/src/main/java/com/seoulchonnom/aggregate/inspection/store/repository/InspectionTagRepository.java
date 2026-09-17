package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionTagJpo;

@Repository
public interface InspectionTagRepository extends JpaRepository<InspectionTagJpo, String> {
	Optional<InspectionTagJpo> findByName(String name);

	List<InspectionTagJpo> findAllByNameIn(Collection<String> names);

	List<InspectionTagJpo> findAllByNameStartingWithOrderByNameAsc(String keyword);

	List<InspectionTagJpo> findAllByIdIn(Collection<String> ids);
}
