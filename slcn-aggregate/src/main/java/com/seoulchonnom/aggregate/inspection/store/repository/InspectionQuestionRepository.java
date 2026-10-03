package com.seoulchonnom.aggregate.inspection.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	/**
	 * 분류 비활성화 검증용(계획 §0-3). 활성 질문이 하나라도 있으면 그 분류는 비활성화할 수 없다.
	 */
	long countByCategoryIdAndEnabledTrue(String categoryId);

	/**
	 * 분류 안에서의 채번(질문 등록/이동)용. 분류에 속한 질문이 없으면 0이다.
	 */
	@Query("SELECT COALESCE(MAX(q.sortOrder), 0) FROM InspectionQuestionJpo q WHERE q.categoryId = :categoryId")
	int findMaxSortOrderByCategoryId(@Param("categoryId") String categoryId);

	/**
	 * 분류 목록 응답의 enabledQuestionCount용.
	 */
	@Query("SELECT q.categoryId, COUNT(q) FROM InspectionQuestionJpo q WHERE q.enabled = true GROUP BY q.categoryId")
	List<Object[]> countEnabledGroupByCategoryId();
}
