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
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.exception.HouseBudgetPolicyNotFoundException;
import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.store.ComplexMapFilter;
import com.seoulchonnom.aggregate.house.store.projection.AreaStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexMapPdo;
import com.seoulchonnom.aggregate.house.store.projection.DistrictCountPdo;
import com.seoulchonnom.aggregate.house.store.projection.DongSummaryPdo;
import com.seoulchonnom.aggregate.house.store.projection.RankingRowPdo;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.ComplexGroupBy;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;
import com.seoulchonnom.spec.house.facade.sdo.ComplexCardRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexMapListRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSearchItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSearchRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryItemRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryRdo;
import com.seoulchonnom.spec.house.facade.sdo.HouseMetaRdo;
import com.seoulchonnom.spec.house.facade.sdo.RankingListRdo;
import com.seoulchonnom.aggregate.house.store.HouseBatchRunStore;
import com.seoulchonnom.aggregate.house.store.HouseBudgetPolicyStore;
import com.seoulchonnom.aggregate.house.store.HouseCollectUnitStore;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseStatStore;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;
import com.seoulchonnom.spec.house.facade.sdo.BudgetPolicyRdo;
import com.seoulchonnom.spec.house.mapper.HouseBudgetPolicyMapperImpl;

class HouseQueryFlowTest {
	// 서울 기준 2026-11-04 00:30 = UTC 2026-11-03 15:30. 시계가 서울 날짜를 써야 한다.
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:30:00Z"), ZoneId.of("Asia/Seoul"));
	private final HouseBudgetPolicyStore store = mock(HouseBudgetPolicyStore.class);
	private final HouseComplexStore complexStore = mock(HouseComplexStore.class);
	private final HouseStatStore statStore = mock(HouseStatStore.class);
	private final HouseCollectUnitStore collectUnitStore = mock(HouseCollectUnitStore.class);
	private final HouseBatchRunStore batchRunStore = mock(HouseBatchRunStore.class);
	private final HouseQueryFlow flow = new HouseQueryFlow(store, new HouseBudgetPolicyMapperImpl(), complexStore,
		statStore, collectUnitStore, batchRunStore, clock);

	@Test
	void getBudgetPolicy_shouldAskStoreWithSeoulToday() {
		HouseBudgetPolicy policy = HouseBudgetPolicy.builder().effectiveFrom(LocalDate.of(2025, 10, 16))
			.ltvFirstTime(new BigDecimal("0.700")).ltvNonFirst(new BigDecimal("0.400"))
			.priceCaps(List.of(new PriceCap(null, 20000))).build();
		when(store.findEffective(LocalDate.of(2026, 11, 4))).thenReturn(policy);

		BudgetPolicyRdo rdo = flow.getBudgetPolicy();

		assertThat(rdo.getLtv()).containsOnlyKeys("FIRST_TIME", "NON_FIRST");
		assertThat(rdo.getLtv().get("FIRST_TIME")).isEqualByComparingTo("0.7");
		assertThat(rdo.getPolicyId()).isEqualTo(policy.getId());
		assertThat(rdo.getPriceCaps()).hasSize(1);
	}

	@Test
	void getBudgetPolicy_shouldPropagateNotFound() {
		when(store.findEffective(any())).thenThrow(new HouseBudgetPolicyNotFoundException());

		assertThatThrownBy(flow::getBudgetPolicy).isInstanceOf(HouseBudgetPolicyNotFoundException.class);
	}

	// ---- meta

	@Test
	void getMeta_shouldCombineDataAsOfAndLastStatsSuccess() {
		when(collectUnitStore.findDataAsOf(LocalDate.of(2026, 11, 4), 25)).thenReturn(LocalDate.of(2026, 11, 3));
		when(batchRunStore.findLastSuccessRunDate(BatchStep.STATS_BUILD)).thenReturn(Optional.of(LocalDate.of(2026, 11, 3)));

		HouseMetaRdo meta = flow.getMeta();

		assertThat(meta.getDataAsOf()).isEqualTo("2026-11-03");
		assertThat(meta.getStatsComputedDate()).isEqualTo("2026-11-03");
	}

	@Test
	void getMeta_shouldReturnNullsWhenNothingCollectedYet() {
		when(batchRunStore.findLastSuccessRunDate(BatchStep.STATS_BUILD)).thenReturn(Optional.empty());

		HouseMetaRdo meta = flow.getMeta();

		assertThat(meta.getDataAsOf()).isNull();
		assertThat(meta.getStatsComputedDate()).isNull();
	}

	// ---- complexes 검증

	private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(complexStore);
	}

	@Test
	void getComplexes_shouldRejectWhenNeitherBoundsNorLawdCd() {
		assertInvalid(() -> flow.getComplexes(null, null, null, null, null, AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldRejectWhenBothBoundsAndLawdCd() {
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.1, "11230", AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldRejectPartialBounds() {
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, null, null, AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldRejectBoundsSideOver02Degrees() {
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.7001, 127.1, null, AreaBucket.B59, null, null));
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.2001, null, AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldAcceptSideOfExactly02Degrees() {
		// 37.62 - 37.42는 double로 0.20000000000000284라 BigDecimal로 재야 통과한다.
		when(complexStore.findMapItems(any(), any(), anyInt())).thenReturn(List.of());

		ComplexMapListRdo result = flow.getComplexes(37.42, 126.9, 37.62, 127.1, null, AreaBucket.B59, null, null);

		assertThat(result.getItems()).isEmpty();
	}

	@Test
	void getComplexes_shouldRejectFlippedBoundsAndNonFinite() {
		assertInvalid(() -> flow.getComplexes(37.6, 127.0, 37.5, 127.1, null, AreaBucket.B59, null, null));
		assertInvalid(() -> flow.getComplexes(37.5, 127.1, 37.6, 127.0, null, AreaBucket.B59, null, null));
		assertInvalid(() -> flow.getComplexes(Double.NaN, 127.0, 37.6, 127.1, null, AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldRejectAreaMissingOrOther() {
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, null, null, null));
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.OTHER, null, null));
	}

	@Test
	void getComplexes_shouldRejectUnknownLawdCd() {
		assertInvalid(() -> flow.getComplexes(null, null, null, null, "99999", AreaBucket.B59, null, null));
	}

	@Test
	void getComplexes_shouldRejectBadPrices() {
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.B59, -1L, null));
		assertInvalid(() -> flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.B59, 50000L, 40000L));
	}

	@Test
	void getComplexes_shouldAllowSinglePriceBoundAndPassOpenEnd() {
		when(complexStore.findMapItems(any(), any(), anyInt())).thenReturn(List.of());

		flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.B59, null, 57340L);

		ArgumentCaptor<ComplexMapFilter> captor = ArgumentCaptor.forClass(ComplexMapFilter.class);
		verify(complexStore).findMapItems(eq(AreaBucket.B59), captor.capture(), eq(300));
		assertThat(captor.getValue().hasPrice()).isTrue();
		assertThat(captor.getValue().minPrice()).isZero();
		assertThat(captor.getValue().maxPrice()).isEqualTo(57340L);
		assertThat(captor.getValue().bounded()).isTrue();
		assertThat(captor.getValue().byDistrict()).isFalse();
		assertThat(captor.getValue().lawdCd()).isEmpty();
	}

	@Test
	void getComplexes_shouldUseDistrictFilterWithoutPriceByDefault() {
		when(complexStore.findMapItems(any(), any(), anyInt())).thenReturn(List.of());

		flow.getComplexes(null, null, null, null, "11230", AreaBucket.B84, null, null);

		ArgumentCaptor<ComplexMapFilter> captor = ArgumentCaptor.forClass(ComplexMapFilter.class);
		verify(complexStore).findMapItems(eq(AreaBucket.B84), captor.capture(), eq(300));
		assertThat(captor.getValue().byDistrict()).isTrue();
		assertThat(captor.getValue().lawdCd()).isEqualTo("11230");
		assertThat(captor.getValue().bounded()).isFalse();
		assertThat(captor.getValue().hasPrice()).isFalse();
	}

	@Test
	void getComplexes_shouldMapRowsAndFlagTruncatedOnlyOverLimit() {
		ComplexMapPdo priced = new ComplexMapPdo("C1", "래미안", "11230", "전농동", 37.57, 127.04, 1999, 380, 45000L, 3, 5);
		ComplexMapPdo unpriced = new ComplexMapPdo("C2", "무가격", "11230", "답십리동", 37.56, 127.05, null, null, null, null,
			null);
		when(complexStore.findMapItems(any(), any(), anyInt())).thenReturn(List.of(priced, unpriced));
		when(complexStore.countMapItems(any(), any())).thenReturn(301L);

		ComplexMapListRdo result = flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.B59, null, null);

		assertThat(result.getTotal()).isEqualTo(301);
		assertThat(result.isTruncated()).isTrue();
		assertThat(result.getItems().get(0).getDistrictName()).isEqualTo("동대문구");
		assertThat(result.getItems().get(0).getRecentPrice().getPriceManwon()).isEqualTo(45000);
		assertThat(result.getItems().get(0).getRecentPrice().getWindowMonths()).isEqualTo(3);
		assertThat(result.getItems().get(1).getRecentPrice()).isNull();

		when(complexStore.countMapItems(any(), any())).thenReturn(300L);
		assertThat(flow.getComplexes(37.5, 127.0, 37.6, 127.1, null, AreaBucket.B59, null, null).isTruncated())
			.isFalse();
	}

	// ---- summary

	@Test
	void getSummary_shouldRejectBoundsForDistrict() {
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DISTRICT, AreaBucket.B59, null, null, 37.5, null, null, null));
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DISTRICT, AreaBucket.B59, null, null, 37.5, 127.0, 37.6, 127.1));
	}

	@Test
	void getSummary_shouldRequireBoundsForDong() {
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DONG, AreaBucket.B59, null, null, null, null, null, null));
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DONG, AreaBucket.B59, null, null, 37.5, 127.0, 37.6, null));
	}

	@Test
	void getSummary_shouldAllowDongSideUpTo10DegreesAndRejectOver() {
		when(complexStore.summarizeByDong(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
			.thenReturn(List.of());

		flow.getSummary(ComplexGroupBy.DONG, AreaBucket.B59, null, null, 37.0, 126.5, 38.0, 127.5);

		clearInvocations(complexStore);
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DONG, AreaBucket.B59, null, null, 37.0, 126.5, 38.0001, 127.5));
	}

	@Test
	void getSummary_shouldRejectMissingGroupByAndOtherArea() {
		assertInvalid(() -> flow.getSummary(null, AreaBucket.B59, null, null, null, null, null, null));
		assertInvalid(() -> flow.getSummary(ComplexGroupBy.DISTRICT, AreaBucket.OTHER, null, null, null, null, null, null));
	}

	@Test
	void getSummary_shouldAlwaysReturn25DistrictsWithZeroForMissingOnes() {
		when(complexStore.countByDistrict(AreaBucket.B59, null, null))
			.thenReturn(List.of(new DistrictCountPdo("11680", 12), new DistrictCountPdo("11230", 3)));

		ComplexSummaryRdo result = flow.getSummary(ComplexGroupBy.DISTRICT, AreaBucket.B59, null, null, null, null, null,
			null);

		assertThat(result.getGroupBy()).isEqualTo(ComplexGroupBy.DISTRICT);
		assertThat(result.getItems()).hasSize(25);
		assertThat(result.getItems()).extracting(ComplexSummaryItemRdo::getCode)
			.containsExactlyElementsOf(HouseConstant.DISTRICTS.stream().map(HouseConstant.District::lawdCd).toList());
		ComplexSummaryItemRdo gangnam = result.getItems().stream().filter(i -> i.getCode().equals("11680")).findFirst()
			.orElseThrow();
		assertThat(gangnam.getCount()).isEqualTo(12);
		assertThat(gangnam.getName()).isEqualTo("강남구");
		assertThat(gangnam.getDistrictName()).isEqualTo("강남구");
		assertThat(gangnam.getLawdCd()).isEqualTo("11680");
		assertThat(gangnam.getCenterLat()).isEqualTo(37.518131);
		assertThat(gangnam.getCenterLng()).isEqualTo(127.047301);
		assertThat(result.getItems().stream().filter(i -> i.getCount() == 0)).hasSize(23);
	}

	@Test
	void getSummary_shouldBuildDongCodeFromLawdCdAndUmdCd() {
		when(complexStore.summarizeByDong(AreaBucket.B59, 45872L, 57340L, 37.54, 126.98, 37.62, 127.1))
			.thenReturn(List.of(new DongSummaryPdo("11305", "10100", "미아동", 37.6131, 127.0298, 12),
				new DongSummaryPdo("11305", null, "미상", 37.6, 127.0, 1)));

		ComplexSummaryRdo result = flow.getSummary(ComplexGroupBy.DONG, AreaBucket.B59, 45872L, 57340L, 37.54, 126.98,
			37.62, 127.1);

		assertThat(result.getItems()).hasSize(1);
		ComplexSummaryItemRdo item = result.getItems().get(0);
		assertThat(item.getCode()).isEqualTo("1130510100");
		assertThat(item.getName()).isEqualTo("미아동");
		assertThat(item.getDistrictName()).isEqualTo("강북구");
		assertThat(item.getCount()).isEqualTo(12);
		assertThat(item.getCenterLat()).isEqualTo(37.6131);
	}

	// ---- card

	@Test
	void getComplex_shouldThrowNotFound() {
		when(complexStore.findById("X")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> flow.getComplex("X", AreaBucket.B59)).isInstanceOf(HouseComplexNotFoundException.class);
	}

	@Test
	void getComplex_shouldRejectOtherAreaBeforeLookup() {
		assertInvalid(() -> flow.getComplex("C1", AreaBucket.OTHER));
		assertInvalid(() -> flow.getComplex("C1", null));
	}

	private HouseComplex card() {
		HouseComplex complex = HouseComplex.builder().aptSeq("s").lawdCd("11230").umdName("전농동").aptName("래미안")
			.buildYear(1999).households(380).build();
		complex.setId("C1");
		return complex;
	}

	@Test
	void getComplex_shouldRenderRecentPriceFromStat() {
		when(complexStore.findById("C1")).thenReturn(Optional.of(card()));
		when(statStore.findAreaStat("C1", AreaBucket.B59)).thenReturn(Optional.of(new AreaStatPdo(45000L, 3, 5)));

		ComplexCardRdo rdo = flow.getComplex("C1", AreaBucket.B59);

		assertThat(rdo.getName()).isEqualTo("래미안");
		assertThat(rdo.getDistrictName()).isEqualTo("동대문구");
		assertThat(rdo.getLatitude()).isNull();
		assertThat(rdo.getRecentPrice().getPriceManwon()).isEqualTo(45000);
		assertThat(rdo.getRecentPrice().getSample()).isEqualTo(5);
	}

	@Test
	void getComplex_shouldReturnNullRecentPriceWhenNoStatRowOrNoPrice() {
		when(complexStore.findById("C1")).thenReturn(Optional.of(card()));
		when(statStore.findAreaStat("C1", AreaBucket.B59)).thenReturn(Optional.empty());
		assertThat(flow.getComplex("C1", AreaBucket.B59).getRecentPrice()).isNull();

		when(statStore.findAreaStat("C1", AreaBucket.B84)).thenReturn(Optional.of(new AreaStatPdo(null, null, 0)));
		assertThat(flow.getComplex("C1", AreaBucket.B84).getRecentPrice()).isNull();
	}

	@Test
	void getComplex_shouldUseApprovalYearForBuildYearWhenPresent() {
		HouseComplex complex = card();
		complex.setApprovalDate(LocalDate.of(2001, 5, 3));
		when(complexStore.findById("C1")).thenReturn(Optional.of(complex));
		when(statStore.findAreaStat("C1", AreaBucket.B59)).thenReturn(Optional.empty());

		assertThat(flow.getComplex("C1", AreaBucket.B59).getBuildYear()).isEqualTo(2001);
	}

	@Test
	void getComplex_shouldFallBackToTradeBuildYearWithoutApprovalDate() {
		when(complexStore.findById("C1")).thenReturn(Optional.of(card()));
		when(statStore.findAreaStat("C1", AreaBucket.B59)).thenReturn(Optional.empty());

		assertThat(flow.getComplex("C1", AreaBucket.B59).getBuildYear()).isEqualTo(1999);
	}

	private HouseComplex searchable(String id, String name, String lawdCd, Integer buildYear, LocalDate approvalDate) {
		HouseComplex complex = HouseComplex.builder().aptSeq("s-" + id).lawdCd(lawdCd).umdName("전농동").aptName(name)
			.buildYear(buildYear).approvalDate(approvalDate).build();
		complex.setId(id);
		return complex;
	}

	@Test
	void searchComplexes_shouldTrimKeywordDefaultSizeAndRenderItems() {
		when(complexStore.searchByName("래미안", 10)).thenReturn(List.of(
			searchable("C1", "래미안 전농", "11230", 2014, null),
			searchable("C2", "래미안2차", "11230", 2010, LocalDate.of(2011, 3, 2))));

		ComplexSearchRdo rdo = flow.searchComplexes("  래미안 ", null);

		assertThat(rdo.getItems()).extracting(ComplexSearchItemRdo::getComplexId).containsExactly("C1", "C2");
		ComplexSearchItemRdo first = rdo.getItems().get(0);
		assertThat(first.getName()).isEqualTo("래미안 전농");
		assertThat(first.getDistrictName()).isEqualTo("동대문구");
		assertThat(first.getUmdName()).isEqualTo("전농동");
		assertThat(first.getBuildYear()).isEqualTo(2014);
		// 사용승인연도가 있으면 그 연도가 우선이다(비교표 연식과 같은 규칙).
		assertThat(rdo.getItems().get(1).getBuildYear()).isEqualTo(2011);
	}

	@Test
	void searchComplexes_shouldReturnEmptyItemsWhenNothingMatches() {
		when(complexStore.searchByName(anyString(), anyInt())).thenReturn(List.of());

		assertThat(flow.searchComplexes("없는단지", 5).getItems()).isEmpty();
		verify(complexStore).searchByName("없는단지", 5);
	}

	@Test
	void searchComplexes_shouldRejectKeywordShorterThanTwoCharsAfterTrim() {
		for (String keyword : new String[] {null, "", " ", "가", " 가 "}) {
			assertThatThrownBy(() -> flow.searchComplexes(keyword, 10)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		}
		verify(complexStore, never()).searchByName(anyString(), anyInt());
	}

	@Test
	void searchComplexes_shouldAcceptSizeBetweenOneAndTwenty() {
		when(complexStore.searchByName(anyString(), anyInt())).thenReturn(List.of());

		flow.searchComplexes("래미안", 1);
		flow.searchComplexes("래미안", 20);

		verify(complexStore).searchByName("래미안", 1);
		verify(complexStore).searchByName("래미안", 20);
	}

	@Test
	void searchComplexes_shouldRejectSizeOutOfRange() {
		for (int size : new int[] {0, -1, 21}) {
			assertThatThrownBy(() -> flow.searchComplexes("래미안", size)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		}
		verify(complexStore, never()).searchByName(anyString(), anyInt());
	}

	@Test
	void getRankings_shouldMapRowsAndTakePeriodFromFirstRow() {
		LocalDate from = LocalDate.of(2025, 10, 21);
		LocalDate to = LocalDate.of(2026, 10, 20);
		when(statStore.findRankings(RankingType.LEADER, AreaBucket.B84)).thenReturn(List.of(
			new RankingRowPdo(1, "C1", "OO아파트", "11680", 37.49, 127.06, 2014, new BigDecimal("84.90"), 1466,
				new BigDecimal("250000.00"), 26000L, 6, null, from, to),
			new RankingRowPdo(2, "C2", "좌표없음", "11650", null, null, null, null, null,
				new BigDecimal("200000.00"), null, 1, RankingBadge.LOW_SAMPLE, from, to),
			new RankingRowPdo(3, null, null, "11110", null, null, null, null, null, null, null, 0,
				RankingBadge.NO_TRADE, from, to)));

		RankingListRdo rdo = flow.getRankings(RankingType.LEADER, AreaBucket.B84);

		assertThat(rdo.getType()).isEqualTo(RankingType.LEADER);
		assertThat(rdo.getArea()).isEqualTo(AreaBucket.B84);
		assertThat(rdo.getPeriod().getFrom()).isEqualTo("2025-10-21");
		assertThat(rdo.getPeriod().getTo()).isEqualTo("2026-10-20");
		assertThat(rdo.getItems()).hasSize(3);
		assertThat(rdo.getItems().get(0).getDistrictName()).isEqualTo("강남구");
		assertThat(rdo.getItems().get(0).getAvgExclusiveArea()).isEqualByComparingTo("84.9");
		assertThat(rdo.getItems().get(0).getSample()).isEqualTo(6);
		assertThat(rdo.getItems().get(1).getLatitude()).isNull();
		assertThat(rdo.getItems().get(1).getBadge()).isEqualTo(RankingBadge.LOW_SAMPLE);
		assertThat(rdo.getItems().get(2).getComplexId()).isNull();
		assertThat(rdo.getItems().get(2).getValue()).isNull();
		assertThat(rdo.getItems().get(2).getDistrictName()).isEqualTo("종로구");
	}

	@Test
	void getRankings_shouldReturnEmptyItemsAndNullPeriodWhenNoRows() {
		when(statStore.findRankings(RankingType.RISE, AreaBucket.B59)).thenReturn(List.of());

		RankingListRdo rdo = flow.getRankings(RankingType.RISE, AreaBucket.B59);

		assertThat(rdo.getItems()).isEmpty();
		assertThat(rdo.getPeriod()).isNull();
	}

	@Test
	void getRankings_shouldRejectMissingTypeAndNonBucketArea() {
		assertThatThrownBy(() -> flow.getRankings(null, AreaBucket.B59)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThatThrownBy(() -> flow.getRankings(RankingType.RISE, null)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> flow.getRankings(RankingType.RISE, AreaBucket.OTHER))
			.isInstanceOf(BusinessException.class);
		verifyNoInteractions(statStore);
	}
}
