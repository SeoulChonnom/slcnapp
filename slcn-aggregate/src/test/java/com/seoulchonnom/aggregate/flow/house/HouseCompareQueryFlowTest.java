package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseReferenceStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.aggregate.house.store.projection.CompareStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexRedevelopmentPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexSchoolPdo;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.FarSource;
import com.seoulchonnom.spec.house.entity.vo.SampleStatus;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;
import com.seoulchonnom.spec.house.facade.sdo.CompareColumnRdo;
import com.seoulchonnom.spec.house.facade.sdo.CompareRdo;
import com.seoulchonnom.spec.house.facade.sdo.PriceChangeRdo;

class HouseCompareQueryFlowTest {
	private final HouseComplexStore complexStore = mock(HouseComplexStore.class);
	private final HouseStatStore statStore = mock(HouseStatStore.class);
	private final HouseReferenceStore referenceStore = mock(HouseReferenceStore.class);
	private final HouseBatchRunStore batchRunStore = mock(HouseBatchRunStore.class);
	// 서울 기준 2026-11-04 00:30. 시계가 서울 날짜(연도)를 써야 한다.
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:30:00Z"), ZoneId.of("Asia/Seoul"));
	private HouseCompareQueryFlow flow;

	@BeforeEach
	void setUp() {
		flow = new HouseCompareQueryFlow(complexStore, statStore, referenceStore, batchRunStore, clock);
		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC)).thenReturn(Optional.empty());
		when(statStore.findCompareStats(any())).thenReturn(List.of());
		when(referenceStore.findComplexSchools(any())).thenReturn(List.of());
		when(referenceStore.findComplexRedevelopments(any())).thenReturn(List.of());
	}

	private HouseComplex complex(String id, String name) {
		HouseComplex complex = HouseComplex.builder().aptSeq("s-" + id).lawdCd("11230").umdName("전농동")
			.aptName(name).buildYear(1999).households(380).farPct(new BigDecimal("365.74")).farSource(FarSource.RECAP)
			.build();
		complex.setId(id);
		return complex;
	}

	private void complexesExist(HouseComplex... complexes) {
		Map<String, HouseComplex> byId = new java.util.LinkedHashMap<>();
		for (HouseComplex complex : complexes) {
			byId.put(complex.getId(), complex);
		}
		when(complexStore.findByIds(any())).thenReturn(byId);
	}

	/** 스펙 예시와 같은 값: 3개월 5건, 1년 전 3건, 3년 전 2건. */
	private CompareStatPdo stat(String complexId, AreaBucket bucket) {
		return new CompareStatPdo(complexId, bucket, new BigDecimal("59.80"), 45000L, 3, 5, 45000L, 5, 43000L, 3,
			new BigDecimal("4.65"), 2000L, 40000L, 2, new BigDecimal("12.50"), 5000L);
	}

	@Test
	void compare_shouldRenderColumnWithSpecFields() {
		complexesExist(complex("C1", "OO아파트"));
		when(statStore.findCompareStats(any())).thenReturn(List.of(stat("C1", AreaBucket.B59)));
		when(referenceStore.findComplexSchools(any())).thenReturn(List.of(
			new ComplexSchoolPdo("C1", "E", "OO초등학교", 604), new ComplexSchoolPdo("C1", "H", "OO고등학교", 1200)));

		CompareRdo rdo = flow.compare("C1:B59");

		assertThat(rdo.getMissingIds()).isEmpty();
		assertThat(rdo.getColumns()).hasSize(1);
		CompareColumnRdo column = rdo.getColumns().get(0);
		assertThat(column.getComplexId()).isEqualTo("C1");
		assertThat(column.getName()).isEqualTo("OO아파트");
		assertThat(column.getDistrictName()).isEqualTo("동대문구");
		assertThat(column.getUmdName()).isEqualTo("전농동");
		assertThat(column.getArea()).isEqualTo(AreaBucket.B59);
		assertThat(column.getRecentPrice().getPriceManwon()).isEqualTo(45000L);
		assertThat(column.getRecentPrice().getWindowMonths()).isEqualTo(3);
		assertThat(column.getRecentPrice().getSample()).isEqualTo(5);
		assertThat(column.getHouseholds()).isEqualTo(380);
		assertThat(column.getBuildYear()).isEqualTo(1999);
		assertThat(column.getAgeYears()).isEqualTo(27);
		assertThat(column.getAreaInfo().getAvgExclusiveArea()).isEqualByComparingTo("59.8");
		assertThat(column.getAreaInfo().getBucket()).isEqualTo(AreaBucket.B59);
		assertThat(column.getFar().getPct()).isEqualByComparingTo("365.74");
		assertThat(column.getFar().getSource()).isEqualTo(FarSource.RECAP);
		assertThat(column.getSchools()).extracting("level", "name", "distanceM")
			.containsExactly(tuple(SchoolLevel.E, "OO초등학교", 604), tuple(SchoolLevel.H, "OO고등학교", 1200));
		PriceChangeRdo change1y = column.getChange1y();
		assertThat(change1y.getFromManwon()).isEqualTo(43000L);
		assertThat(change1y.getToManwon()).isEqualTo(45000L);
		assertThat(change1y.getPct()).isEqualByComparingTo("4.65");
		assertThat(change1y.getChangeManwon()).isEqualTo(2000L);
		assertThat(change1y.getSampleFrom()).isEqualTo(3);
		assertThat(change1y.getSampleTo()).isEqualTo(5);
		assertThat(change1y.getSampleStatus()).isEqualTo(SampleStatus.OK);
		PriceChangeRdo change3y = column.getChange3y();
		assertThat(change3y.getFromManwon()).isEqualTo(40000L);
		assertThat(change3y.getPct()).isEqualByComparingTo("12.50");
		assertThat(change3y.getSampleStatus()).isEqualTo(SampleStatus.LOW);
	}

	@Test
	void compare_shouldListMissingIdsInRequestOrderWithoutDuplicates() {
		complexesExist(complex("C1", "A"));

		CompareRdo rdo = flow.compare("C9:B59,C1:B59,C9:B84");

		assertThat(rdo.getMissingIds()).containsExactly("C9");
		assertThat(rdo.getColumns()).extracting(CompareColumnRdo::getComplexId).containsExactly("C1");
	}

	@Test
	void compare_shouldListEveryMissingIdInRequestOrder() {
		complexesExist(complex("C1", "A"));

		assertThat(flow.compare("C9:B59,C1:B59,C8:B84").getMissingIds()).containsExactly("C9", "C8");
	}

	@Test
	void compare_shouldReturnEmptyColumnsAnd200ShapeWhenEveryComplexIsMissing() {
		complexesExist();

		CompareRdo rdo = flow.compare("C9:B59");

		assertThat(rdo.getMissingIds()).containsExactly("C9");
		assertThat(rdo.getColumns()).isEmpty();
		verify(statStore, never()).findCompareStats(any());
	}

	@Test
	void compare_shouldKeepRequestOrderForColumnsAndAllowSameComplexWithBothAreas() {
		complexesExist(complex("C1", "A"), complex("C2", "B"));
		when(statStore.findCompareStats(any()))
			.thenReturn(List.of(stat("C1", AreaBucket.B59), stat("C1", AreaBucket.B84)));

		CompareRdo rdo = flow.compare("C2:B84,C1:B84,C1:B59");

		assertThat(rdo.getColumns()).extracting(CompareColumnRdo::getComplexId, CompareColumnRdo::getArea)
			.containsExactly(tuple("C2", AreaBucket.B84), tuple("C1", AreaBucket.B84), tuple("C1", AreaBucket.B59));
	}

	@Test
	void compare_shouldSkipMissingColumnButKeepOthersInOrder() {
		complexesExist(complex("C1", "A"), complex("C3", "C"));

		CompareRdo rdo = flow.compare("C1:B59,C2:B59,C3:B84");

		assertThat(rdo.getColumns()).extracting(CompareColumnRdo::getComplexId).containsExactly("C1", "C3");
		assertThat(rdo.getMissingIds()).containsExactly("C2");
	}

	@Test
	void compare_shouldReturnNullRedevelopmentsWhenNeverSyncedSuccessfully() {
		complexesExist(complex("C1", "A"));
		// 테이블에 행이 있어도(가정) SUCCESS 기록이 없으면 미적재로 본다.
		when(referenceStore.findComplexRedevelopments(any()))
			.thenReturn(List.of(new ComplexRedevelopmentPdo("C1", "OO구역", "재개발", "관리처분인가", 320, "https://x")));

		CompareRdo rdo = flow.compare("C1:B59");

		assertThat(rdo.getColumns().get(0).getRedevelopments()).isNull();
		verify(referenceStore, never()).findComplexRedevelopments(any());
	}

	@Test
	void compare_shouldReturnEmptyListRedevelopmentsWhenSyncedButNoneNearby() {
		complexesExist(complex("C1", "A"));
		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC))
			.thenReturn(Optional.of(LocalDate.of(2026, 10, 1)));

		CompareRdo rdo = flow.compare("C1:B59");

		assertThat(rdo.getColumns().get(0).getRedevelopments()).isNotNull().isEmpty();
	}

	@Test
	void compare_shouldRenderRedevelopmentsPerComplexWhenSynced() {
		complexesExist(complex("C1", "A"), complex("C2", "B"));
		when(batchRunStore.findLastSuccessRunDate(BatchStep.REDEVELOPMENT_SYNC))
			.thenReturn(Optional.of(LocalDate.of(2026, 10, 1)));
		when(referenceStore.findComplexRedevelopments(any())).thenReturn(List.of(
			new ComplexRedevelopmentPdo("C1", "OO재정비촉진구역", "재개발", "관리처분인가", 320, "https://x")));

		CompareRdo rdo = flow.compare("C1:B59,C2:B59");

		assertThat(rdo.getColumns().get(0).getRedevelopments()).extracting("name", "projectType", "stage", "distanceM",
			"sourceUrl").containsExactly(tuple("OO재정비촉진구역", "재개발", "관리처분인가", 320, "https://x"));
		assertThat(rdo.getColumns().get(1).getRedevelopments()).isEmpty();
	}

	@Test
	void compare_shouldStillRenderColumnWithNullPriceFieldsWhenNoStatRow() {
		complexesExist(complex("C1", "A"));

		CompareColumnRdo column = flow.compare("C1:B84").getColumns().get(0);

		assertThat(column.getRecentPrice()).isNull();
		assertThat(column.getChange1y()).isNull();
		assertThat(column.getChange3y()).isNull();
		assertThat(column.getAreaInfo().getAvgExclusiveArea()).isNull();
		assertThat(column.getAreaInfo().getBucket()).isEqualTo(AreaBucket.B84);
		assertThat(column.getArea()).isEqualTo(AreaBucket.B84);
		// 단지 자체의 값은 통계와 무관하게 나온다.
		assertThat(column.getHouseholds()).isEqualTo(380);
		assertThat(column.getFar()).isNotNull();
		assertThat(column.getSchools()).isEmpty();
	}

	@Test
	void compare_shouldRenderNullRecentPriceWhenStatHasNoTradesInSixMonths() {
		complexesExist(complex("C1", "A"));
		when(statStore.findCompareStats(any())).thenReturn(List.of(new CompareStatPdo("C1", AreaBucket.B59, null, null,
			null, 0, null, 0, null, 0, null, null, null, 0, null, null)));

		CompareColumnRdo column = flow.compare("C1:B59").getColumns().get(0);

		assertThat(column.getRecentPrice()).isNull();
		PriceChangeRdo change = column.getChange1y();
		assertThat(change.getSampleStatus()).isEqualTo(SampleStatus.INSUFFICIENT);
		assertThat(change.getFromManwon()).isNull();
		assertThat(change.getToManwon()).isNull();
		assertThat(change.getPct()).isNull();
		assertThat(change.getChangeManwon()).isNull();
		assertThat(change.getSampleFrom()).isZero();
		assertThat(change.getSampleTo()).isZero();
	}

	@Test
	void compare_shouldKeepAveragesOnlyOnSideWithTradesWhenInsufficient() {
		complexesExist(complex("C1", "A"));
		// 최근 3개월 5건, 1년 전 1건: 변동률은 못 내지만 두 평균은 건수가 있으니 낸다.
		when(statStore.findCompareStats(any())).thenReturn(List.of(new CompareStatPdo("C1", AreaBucket.B59,
			new BigDecimal("59.8"), 45000L, 3, 5, 45000L, 5, 43000L, 1, null, null, null, 0, null, null)));

		CompareColumnRdo column = flow.compare("C1:B59").getColumns().get(0);

		PriceChangeRdo change1y = column.getChange1y();
		assertThat(change1y.getSampleStatus()).isEqualTo(SampleStatus.INSUFFICIENT);
		assertThat(change1y.getFromManwon()).isEqualTo(43000L);
		assertThat(change1y.getToManwon()).isEqualTo(45000L);
		assertThat(change1y.getPct()).isNull();
		assertThat(change1y.getChangeManwon()).isNull();
		// 3년 전 0건이면 from만 비고 to는 남는다.
		PriceChangeRdo change3y = column.getChange3y();
		assertThat(change3y.getFromManwon()).isNull();
		assertThat(change3y.getToManwon()).isEqualTo(45000L);
		assertThat(change3y.getSampleStatus()).isEqualTo(SampleStatus.INSUFFICIENT);
	}

	@Test
	void compare_shouldPreferApprovalYearForBuildYearAndComputeAge() {
		HouseComplex complex = complex("C1", "A");
		complex.setApprovalDate(LocalDate.of(2004, 12, 30));
		complexesExist(complex);

		CompareColumnRdo column = flow.compare("C1:B59").getColumns().get(0);

		assertThat(column.getBuildYear()).isEqualTo(2004);
		assertThat(column.getAgeYears()).isEqualTo(22);
	}

	@Test
	void compare_shouldReturnNullBuildYearAgeAndFarWhenUnknown() {
		HouseComplex complex = complex("C1", "A");
		complex.setBuildYear(null);
		complex.setFarPct(null);
		complex.setFarSource(null);
		complex.setHouseholds(null);
		complexesExist(complex);

		CompareColumnRdo column = flow.compare("C1:B59").getColumns().get(0);

		assertThat(column.getBuildYear()).isNull();
		assertThat(column.getAgeYears()).isNull();
		assertThat(column.getFar()).isNull();
		assertThat(column.getHouseholds()).isNull();
	}

	@Test
	void compare_shouldRejectInvalidItemsBeforeTouchingStores() {
		assertThatThrownBy(() -> flow.compare("C1:B60")).isInstanceOf(HouseValidationException.class);
		verifyNoInteractions(complexStore, statStore, referenceStore);
	}
}
