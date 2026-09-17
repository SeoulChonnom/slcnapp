package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyJpo;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;

@Repository
public interface ViewedPropertyRepository extends JpaRepository<ViewedPropertyJpo, String> {
	/**
	 * sortOrder 중복을 허용하므로 registeredTime과 id를 2차 키로 붙인다.
	 * 없으면 동률 행의 순서를 PostgreSQL이 보장하지 않아 새로고침마다 화면이 뒤바뀐다.
	 */
	List<ViewedPropertyJpo> findAllByInspectionVisitIdOrderBySortOrderAscRegisteredTimeAscIdAsc(
		String inspectionVisitId);

	List<ViewedPropertyJpo> findAllByIdIn(Collection<String> ids);

	/**
	 * answers를 읽지 않는 projection 조회.
	 */
	List<ViewedPropertySummaryPdo> findAllByInspectionVisitIdIn(Collection<String> inspectionVisitIds);

	long countByInspectionVisitId(String inspectionVisitId);

	void deleteByInspectionVisitId(String inspectionVisitId);
}
