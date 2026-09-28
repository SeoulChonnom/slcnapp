package com.seoulchonnom.aggregate.inspection.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryConflictException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionQuestionCategoryJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionQuestionCategoryRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;

class InspectionQuestionCategoryStoreTest {
	private final InspectionQuestionCategoryRepository inspectionQuestionCategoryRepository =
		mock(InspectionQuestionCategoryRepository.class);
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore = new InspectionQuestionCategoryStore(
		inspectionQuestionCategoryRepository, new InspectionQuestionCategoryJpoMapper());

	/**
	 * 버전 충돌은 질문 Store(InspectionQuestionStore.save)와 같은 방식으로 409로 바꾼다(계획 §3).
	 */
	@Test
	void save_shouldConvertOptimisticLockFailureToConflict() {
		when(inspectionQuestionCategoryRepository.saveAndFlush(any()))
			.thenThrow(new ObjectOptimisticLockingFailureException("inspection_question_category", "id"));

		assertThatThrownBy(() -> inspectionQuestionCategoryStore.save(
			new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001", "채광·환기", 1)))
			.isInstanceOf(InspectionQuestionCategoryConflictException.class);
	}

	/**
	 * 이름 유니크 위반은 앱 레벨 중복 검사(Logic)를 통과한 두 요청이 동시에 들어올 때의 백스톱이다.
	 */
	@Test
	void save_shouldConvertUniqueViolationToDuplicated() {
		when(inspectionQuestionCategoryRepository.saveAndFlush(any()))
			.thenThrow(new DataIntegrityViolationException("uk_inspection_question_category_name"));

		assertThatThrownBy(() -> inspectionQuestionCategoryStore.save(
			new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001", "채광·환기", 1)))
			.isInstanceOf(InspectionQuestionCategoryDuplicatedException.class);
	}

	@Test
	void findById_shouldThrowNotFoundWhenMissing() {
		when(inspectionQuestionCategoryRepository.findById("INSPECTION_QUESTION_CATEGORY-9999"))
			.thenReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> inspectionQuestionCategoryStore.findById("INSPECTION_QUESTION_CATEGORY-9999"))
			.isInstanceOf(InspectionQuestionCategoryNotFoundException.class);
	}

	@Test
	void findAllByIds_shouldReturnEmptyListWithoutQueryingWhenIdsAreEmpty() {
		assertThat(inspectionQuestionCategoryStore.findAllByIds(java.util.List.of())).isEmpty();
		verifyNoInteractions(inspectionQuestionCategoryRepository);
	}
}
