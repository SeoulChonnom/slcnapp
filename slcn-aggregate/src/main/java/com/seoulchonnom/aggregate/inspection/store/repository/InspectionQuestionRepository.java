package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionJpo;

@Repository
public interface InspectionQuestionRepository extends JpaRepository<InspectionQuestionJpo, String> {
	/**
	 * sortOrder 중복을 허용하므로 id를 2차 키로 붙인다. 없으면 동률 행의 순서가
	 * 새로고침마다 달라진다.
	 */
	List<InspectionQuestionJpo> findAllByOrderBySortOrderAscIdAsc();

	List<InspectionQuestionJpo> findAllByEnabledTrueOrderBySortOrderAscIdAsc();

	List<InspectionQuestionJpo> findAllByIdIn(Collection<String> ids);
}
