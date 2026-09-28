package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionQuestionCategoryInUseException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionQuestionCategoryException;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryUdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionQuestionCategoryMapper;

class InspectionQuestionCategoryLogicTest {
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore =
		mock(InspectionQuestionCategoryStore.class);
	private final InspectionQuestionStore inspectionQuestionStore = mock(InspectionQuestionStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final InspectionQuestionCategoryLogic inspectionQuestionCategoryLogic = new InspectionQuestionCategoryLogic(
		inspectionQuestionCategoryStore, inspectionQuestionStore, new InspectionQuestionCategoryMapper(),
		idGenerator);

	private void echoSave() {
		when(inspectionQuestionCategoryStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void registerInspectionQuestionCategory_shouldUseGivenSortOrderWhenPositive() {
		when(idGenerator.nextDomainId("INSPECTION_QUESTION_CATEGORY"))
			.thenReturn("INSPECTION_QUESTION_CATEGORY-0001");
		when(inspectionQuestionCategoryStore.findOptionalByName("채광·환기")).thenReturn(Optional.empty());
		echoSave();

		InspectionQuestionCategoryRdo rdo = inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(
			new InspectionQuestionCategoryCdo("채광·환기", 3));

		assertThat(rdo.getCategoryId()).isEqualTo("INSPECTION_QUESTION_CATEGORY-0001");
		assertThat(rdo.getSortOrder()).isEqualTo(3);
		assertThat(rdo.isEnabled()).isTrue();
		assertThat(rdo.getEnabledQuestionCount()).isZero();
		verify(inspectionQuestionCategoryStore, never()).findMaxSortOrder();
	}

	@Test
	void registerInspectionQuestionCategory_shouldAppendToTheEndWhenSortOrderIsNotPositive() {
		when(idGenerator.nextDomainId(anyString())).thenReturn("INSPECTION_QUESTION_CATEGORY-0002");
		when(inspectionQuestionCategoryStore.findOptionalByName("구조·수납")).thenReturn(Optional.empty());
		when(inspectionQuestionCategoryStore.findMaxSortOrder()).thenReturn(3);
		echoSave();

		InspectionQuestionCategoryRdo rdo = inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(
			new InspectionQuestionCategoryCdo("구조·수납", 0));

		assertThat(rdo.getSortOrder()).isEqualTo(4);
	}

	/**
	 * 비활성 분류까지 포함해 중복을 막는다(계획 §0-6).
	 */
	@Test
	void registerInspectionQuestionCategory_shouldRejectDuplicatedNameIncludingDisabled() {
		InspectionQuestionCategory disabled = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		disabled.changeEnabled(false);
		when(inspectionQuestionCategoryStore.findOptionalByName("채광·환기")).thenReturn(Optional.of(disabled));

		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(
			new InspectionQuestionCategoryCdo("채광·환기", 1)))
			.isInstanceOf(InspectionQuestionCategoryDuplicatedException.class)
			.hasMessageContaining("INSPECTION_QUESTION_CATEGORY-0001");
		verify(inspectionQuestionCategoryStore, never()).save(any());
	}

	@Test
	void registerInspectionQuestionCategory_shouldRejectBlankName() {
		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(
			new InspectionQuestionCategoryCdo("   ", 1)))
			.isInstanceOf(InvalidInspectionQuestionCategoryException.class);
		verifyNoInteractions(idGenerator);
	}

	@Test
	void registerInspectionQuestionCategory_shouldRejectNameLongerThanFifty() {
		String tooLong = "가".repeat(51);

		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(
			new InspectionQuestionCategoryCdo(tooLong, 1)))
			.isInstanceOf(InvalidInspectionQuestionCategoryException.class);
	}

	@Test
	void renameInspectionQuestionCategory_shouldAllowKeepingItsOwnName() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		when(inspectionQuestionCategoryStore.findById("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(category);
		when(inspectionQuestionCategoryStore.findOptionalByName("채광·환기")).thenReturn(Optional.of(category));
		when(inspectionQuestionStore.countEnabledByCategoryId("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(0L);
		echoSave();

		InspectionQuestionCategoryRdo rdo = inspectionQuestionCategoryLogic.renameInspectionQuestionCategory(
			"INSPECTION_QUESTION_CATEGORY-0001", new InspectionQuestionCategoryUdo("채광·환기"));

		assertThat(rdo.getName()).isEqualTo("채광·환기");
	}

	@Test
	void renameInspectionQuestionCategory_shouldRejectRenameIntoAnotherCategory() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		InspectionQuestionCategory other = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0002",
			"구조·수납", 2);
		when(inspectionQuestionCategoryStore.findById("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(category);
		when(inspectionQuestionCategoryStore.findOptionalByName("구조·수납")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.renameInspectionQuestionCategory(
			"INSPECTION_QUESTION_CATEGORY-0001", new InspectionQuestionCategoryUdo("구조·수납")))
			.isInstanceOf(InspectionQuestionCategoryDuplicatedException.class);
		assertThat(category.getName()).isEqualTo("채광·환기");
	}

	/**
	 * 활성 질문이 있는 분류는 비활성화할 수 없다(계획 §0-3, A안).
	 */
	@Test
	void changeInspectionQuestionCategoryStatus_shouldRejectDisablingWhenEnabledQuestionsExist() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		when(inspectionQuestionCategoryStore.findById("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(category);
		when(inspectionQuestionStore.countEnabledByCategoryId("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(1L);

		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.changeInspectionQuestionCategoryStatus(
			"INSPECTION_QUESTION_CATEGORY-0001", new InspectionQuestionCategoryStatusUdo(false)))
			.isInstanceOf(InspectionQuestionCategoryInUseException.class);
		assertThat(category.isEnabled()).isTrue();
		verify(inspectionQuestionCategoryStore, never()).save(any());
	}

	@Test
	void changeInspectionQuestionCategoryStatus_shouldAllowDisablingWhenNoEnabledQuestions() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		when(inspectionQuestionCategoryStore.findById("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(category);
		when(inspectionQuestionStore.countEnabledByCategoryId("INSPECTION_QUESTION_CATEGORY-0001")).thenReturn(0L);
		echoSave();

		InspectionQuestionCategoryRdo rdo = inspectionQuestionCategoryLogic.changeInspectionQuestionCategoryStatus(
			"INSPECTION_QUESTION_CATEGORY-0001", new InspectionQuestionCategoryStatusUdo(false));

		assertThat(rdo.isEnabled()).isFalse();
	}

	@Test
	void modifyInspectionQuestionCategoryOrder_shouldRejectUnknownCategory() {
		when(inspectionQuestionCategoryStore.findAllByIds(anyCollection())).thenReturn(List.of());

		assertThatThrownBy(() -> inspectionQuestionCategoryLogic.modifyInspectionQuestionCategoryOrder(
			List.of(new InspectionQuestionCategoryOrderUdo("INSPECTION_QUESTION_CATEGORY-9999", 1))))
			.isInstanceOf(InvalidInspectionQuestionCategoryException.class);
		verify(inspectionQuestionCategoryStore, never()).saveAll(any());
	}

	/**
	 * 요청에 빠진 분류는 기존 순서를 유지한다.
	 */
	@Test
	void modifyInspectionQuestionCategoryOrder_shouldKeepOrderOfCategoriesMissingFromTheRequest() {
		InspectionQuestionCategory first = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		InspectionQuestionCategory second = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0002",
			"구조·수납", 2);
		when(inspectionQuestionCategoryStore.findAllByIds(anyCollection())).thenReturn(List.of(first));

		inspectionQuestionCategoryLogic.modifyInspectionQuestionCategoryOrder(
			List.of(new InspectionQuestionCategoryOrderUdo("INSPECTION_QUESTION_CATEGORY-0001", 5)));

		assertThat(first.getSortOrder()).isEqualTo(5);
		assertThat(second.getSortOrder()).isEqualTo(2);
		verify(inspectionQuestionCategoryStore).saveAll(List.of(first));
	}

	@Test
	void getInspectionQuestionCategories_shouldFillEnabledQuestionCountFromStoreAggregate() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);
		when(inspectionQuestionCategoryStore.findAllEnabled()).thenReturn(List.of(category));
		when(inspectionQuestionStore.countEnabledGroupByCategoryId())
			.thenReturn(Map.of("INSPECTION_QUESTION_CATEGORY-0001", 4));

		List<InspectionQuestionCategoryRdo> categories = inspectionQuestionCategoryLogic
			.getInspectionQuestionCategories(false);

		assertThat(categories).hasSize(1);
		assertThat(categories.get(0).getEnabledQuestionCount()).isEqualTo(4);
	}
}
