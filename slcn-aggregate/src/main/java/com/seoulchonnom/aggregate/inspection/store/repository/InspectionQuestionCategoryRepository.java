package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionCategoryJpo;

@Repository
public interface InspectionQuestionCategoryRepository extends JpaRepository<InspectionQuestionCategoryJpo, String> {
	Optional<InspectionQuestionCategoryJpo> findByName(String name);

	/**
	 * sortOrder 중복을 허용하므로 id를 2차 키로 붙인다. 질문 목록과 같은 이유다.
	 */
	List<InspectionQuestionCategoryJpo> findAllByOrderBySortOrderAscIdAsc();

	List<InspectionQuestionCategoryJpo> findAllByEnabledTrueOrderBySortOrderAscIdAsc();

	List<InspectionQuestionCategoryJpo> findAllByIdIn(Collection<String> ids);

	/**
	 * 등록 시 sortOrder를 0 이하로 보내면 맨 뒤(이 값+1)로 채번한다(계획 §1).
	 */
	@Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM InspectionQuestionCategoryJpo c")
	int findMaxSortOrder();
}
