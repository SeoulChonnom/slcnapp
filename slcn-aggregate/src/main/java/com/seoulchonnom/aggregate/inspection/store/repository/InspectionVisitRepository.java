package com.seoulchonnom.aggregate.inspection.store.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitJpo;

@Repository
public interface InspectionVisitRepository extends JpaRepository<InspectionVisitJpo, String> {
	List<InspectionVisitJpo> findAllByOrderByVisitedAtDescIdAsc();

	List<InspectionVisitJpo> findAllByAreaIdOrderByVisitedAtDescIdAsc(String areaId);

	List<InspectionVisitJpo> findAllByAreaIdIn(Collection<String> areaIds);

	List<InspectionVisitJpo> findAllByIdIn(Collection<String> ids);

	List<InspectionVisitJpo> findAllByVisitedAtBetweenOrderByVisitedAtDescIdAsc(LocalDateTime from, LocalDateTime to);

	long countByAreaId(String areaId);

	boolean existsByAreaId(String areaId);
}
