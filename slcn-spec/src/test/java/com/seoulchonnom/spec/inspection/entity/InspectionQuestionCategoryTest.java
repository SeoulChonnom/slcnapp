package com.seoulchonnom.spec.inspection.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InspectionQuestionCategoryTest {
	private static InspectionQuestionCategory category() {
		return new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001", "채광·환기", 1);
	}

	@Test
	void constructor_shouldStartEnabled() {
		InspectionQuestionCategory category = category();

		assertThat(category.isEnabled()).isTrue();
		assertThat(category.getName()).isEqualTo("채광·환기");
		assertThat(category.getSortOrder()).isEqualTo(1);
	}

	@Test
	void rename_shouldUpdateNameAndModifiedTime() throws InterruptedException {
		InspectionQuestionCategory category = category();
		long before = category.getModifiedTime();
		Thread.sleep(2);

		category.rename("채광 환기");

		assertThat(category.getName()).isEqualTo("채광 환기");
		assertThat(category.getModifiedTime()).isGreaterThan(before);
	}

	@Test
	void changeSortOrder_shouldUpdateSortOrderAndModifiedTime() throws InterruptedException {
		InspectionQuestionCategory category = category();
		long before = category.getModifiedTime();
		Thread.sleep(2);

		category.changeSortOrder(5);

		assertThat(category.getSortOrder()).isEqualTo(5);
		assertThat(category.getModifiedTime()).isGreaterThan(before);
	}

	@Test
	void changeEnabled_shouldUpdateEnabledAndModifiedTime() throws InterruptedException {
		InspectionQuestionCategory category = category();
		long before = category.getModifiedTime();
		Thread.sleep(2);

		category.changeEnabled(false);

		assertThat(category.isEnabled()).isFalse();
		assertThat(category.getModifiedTime()).isGreaterThan(before);
	}
}
