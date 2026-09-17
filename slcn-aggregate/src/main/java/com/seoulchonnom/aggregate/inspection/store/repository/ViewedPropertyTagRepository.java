package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyTagJpo;

@Repository
public interface ViewedPropertyTagRepository extends JpaRepository<ViewedPropertyTagJpo, String> {
	List<ViewedPropertyTagJpo> findAllByViewedPropertyId(String viewedPropertyId);

	List<ViewedPropertyTagJpo> findAllByViewedPropertyIdIn(Collection<String> viewedPropertyIds);

	List<ViewedPropertyTagJpo> findAllByInspectionVisitId(String inspectionVisitId);

	List<ViewedPropertyTagJpo> findAllByTagIdIn(Collection<String> tagIds);

	void deleteByViewedPropertyId(String viewedPropertyId);

	void deleteByInspectionVisitId(String inspectionVisitId);
}
