package com.seoulchonnom.aggregate.house.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.aggregate.house.stat.NearestSchoolCalculator.Nearest;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseReferenceStore;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseSchool;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

class HouseReferenceLogicTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

	private final HouseReferenceStore referenceStore = mock(HouseReferenceStore.class);
	private final HouseComplexStore complexStore = mock(HouseComplexStore.class);
	private final HouseReferenceLogic logic = new HouseReferenceLogic(referenceStore, complexStore);

	@BeforeEach
	void setUp() {
		HouseSchool school = HouseSchool.builder().schoolCode("B1").name("서울초").level(SchoolLevel.E)
			.latitude(37.5010).longitude(127.0).build();
		school.setId("school-uuid");
		when(referenceStore.findAllSchools()).thenReturn(List.of(school));
		when(complexStore.findGeocodedPoints()).thenReturn(List.of(new ComplexPointPdo("C1", 37.5, 127.0)));
		when(referenceStore.replaceComplexSchools(any())).thenAnswer(invocation -> ((List<?>)invocation.getArgument(0)).size());
		when(referenceStore.deleteSchoolsExcept(any())).thenReturn(4);
	}

	@Test
	void replaceSchools_shouldUpsertDeleteStaleThenRebuildNearestInThatOrder() {
		List<SchoolRecord> records = List.of(new SchoolRecord("B1", "서울초", SchoolLevel.E, 37.5010, 127.0),
			new SchoolRecord("B2", "서울중", SchoolLevel.M, 37.5, 127.01));

		SchoolSyncResult result = logic.replaceSchools(records, TODAY);

		InOrder inOrder = inOrder(referenceStore);
		inOrder.verify(referenceStore).upsertSchool(eq(records.get(0)), eq(TODAY), anyLong());
		inOrder.verify(referenceStore).upsertSchool(eq(records.get(1)), eq(TODAY), anyLong());
		inOrder.verify(referenceStore).deleteSchoolsExcept(List.of("B1", "B2"));
		inOrder.verify(referenceStore).findAllSchools();
		inOrder.verify(referenceStore).replaceComplexSchools(any());
		assertThat(result).isEqualTo(new SchoolSyncResult(1, 4, 1, 1));
	}

	@Test
	void replaceSchools_shouldRefuseEmptyListSoExistingSchoolsAreKept() {
		assertThatThrownBy(() -> logic.replaceSchools(List.of(), TODAY)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> logic.replaceSchools(null, TODAY)).isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(referenceStore);
	}

	@Test
	void recomputeNearest_shouldRebuildOnlyNearestWithoutTouchingSchools() {
		SchoolSyncResult result = logic.recomputeNearest();

		assertThat(result).isEqualTo(new SchoolSyncResult(1, 0, 1, 1));
		verify(referenceStore, never()).upsertSchool(any(), any(), anyLong());
		verify(referenceStore, never()).deleteSchoolsExcept(any());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Nearest>> captor = ArgumentCaptor.forClass(List.class);
		verify(referenceStore).replaceComplexSchools(captor.capture());
		assertThat(captor.getValue()).containsExactly(new Nearest("C1", SchoolLevel.E, "school-uuid", 111));
	}
}
