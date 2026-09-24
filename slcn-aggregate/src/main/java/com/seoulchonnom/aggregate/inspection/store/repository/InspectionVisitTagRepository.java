package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitTagJpo;

@Repository
public interface InspectionVisitTagRepository extends JpaRepository<InspectionVisitTagJpo, String> {
	List<InspectionVisitTagJpo> findAllByInspectionVisitId(String inspectionVisitId);

	List<InspectionVisitTagJpo> findAllByInspectionVisitIdIn(Collection<String> inspectionVisitIds);

	List<InspectionVisitTagJpo> findAllByTagIdIn(Collection<String> tagIds);

	void deleteByInspectionVisitId(String inspectionVisitId);
}
