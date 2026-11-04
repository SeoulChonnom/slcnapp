package com.seoulchonnom.aggregate.house.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentRow;
import com.seoulchonnom.aggregate.house.stat.RedevelopmentLinkCalculator.Link;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseRedevelopmentStore;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

class HouseRedevelopmentLogicTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

	private final HouseRedevelopmentStore store = mock(HouseRedevelopmentStore.class);
	private final HouseComplexStore complexStore = mock(HouseComplexStore.class);
	private final HouseRedevelopmentLogic logic = new HouseRedevelopmentLogic(store, complexStore);

	@BeforeEach
	void setUp() {
		when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static RedevelopmentRow row(String code, String name, String jibun) {
		return new RedevelopmentRow(code, "11110", name, "도시정비형 재개발", "건축심의", jibun, "종로1길36", null);
	}

	/** row(code, name, jibun)와 같은 값을 가진, 좌표까지 확정된 기존 구역. */
	private static HouseRedevelopment existing(String code, String name, String jibun) {
		HouseRedevelopment redevelopment = HouseRedevelopment.builder().sourceKey(code).name(name).lawdCd("11110")
			.jibunAddress(jibun).roadAddress("종로1길36").projectType("도시정비형 재개발").stage("건축심의")
			.latitude(37.57).longitude(126.97).geocodeStatus(GeocodeStatus.DONE).syncedDate(LocalDate.of(2026, 8, 1))
			.build();
		redevelopment.setId("id-" + code);
		return redevelopment;
	}

	@Test
	void replaceAll_shouldInsertNewRedevelopmentsAsPendingWithZeroAttempts() {
		when(store.findAll()).thenReturn(List.of());
		when(store.countByGeocodeStatus(GeocodeStatus.PENDING)).thenReturn(1L);

		RedevelopmentReplaceResult result = logic.replaceAll(List.of(row("1460", "수송1-2", "수송동146-12")), TODAY);

		ArgumentCaptor<HouseRedevelopment> saved = ArgumentCaptor.forClass(HouseRedevelopment.class);
		verify(store).save(saved.capture());
		assertThat(saved.getValue().getSourceKey()).isEqualTo("1460");
		assertThat(saved.getValue().getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
		assertThat(saved.getValue().getGeocodeAttempts()).isZero();
		assertThat(saved.getValue().getSyncedDate()).isEqualTo(TODAY);
		assertThat(saved.getValue().getSourceUrl()).isNull();
		assertThat(result).isEqualTo(new RedevelopmentReplaceResult(1, 1, 0, 0, 0, 1));
	}

	@Test
	void replaceAll_shouldNotTouchUnchangedRows() {
		when(store.findAll()).thenReturn(List.of(existing("1460", "수송1-2", "수송동146-12")));

		RedevelopmentReplaceResult result = logic.replaceAll(List.of(row("1460", "수송1-2", "수송동146-12")), TODAY);

		verify(store, never()).save(any());
		verify(store).deleteWithLinks(List.of());
		assertThat(result.unchanged()).isEqualTo(1);
		assertThat(result.inserted()).isZero();
		assertThat(result.updated()).isZero();
	}

	@Test
	void replaceAll_shouldKeepCoordinatesWhenOnlyNameOrStageChanges() {
		HouseRedevelopment current = existing("1460", "옛이름", "수송동146-12");
		when(store.findAll()).thenReturn(List.of(current));
		RedevelopmentRow changed = new RedevelopmentRow("1460", "11110", "새이름", "도시정비형 재개발", "관리처분",
			"수송동146-12", "종로1길36", null);

		RedevelopmentReplaceResult result = logic.replaceAll(List.of(changed), TODAY);

		verify(store).save(current);
		assertThat(current.getName()).isEqualTo("새이름");
		assertThat(current.getStage()).isEqualTo("관리처분");
		assertThat(current.getGeocodeStatus()).isEqualTo(GeocodeStatus.DONE);
		assertThat(current.getLatitude()).isEqualTo(37.57);
		assertThat(result.updated()).isEqualTo(1);
	}

	@Test
	void replaceAll_shouldResetGeocodeToPendingWhenAddressChanges() {
		HouseRedevelopment current = existing("1460", "수송1-2", "수송동146-12");
		current.setGeocodeAttempts(2);
		when(store.findAll()).thenReturn(List.of(current));

		logic.replaceAll(List.of(row("1460", "수송1-2", "수송동146-13")), TODAY);

		verify(store).save(current);
		assertThat(current.getJibunAddress()).isEqualTo("수송동146-13");
		assertThat(current.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
		assertThat(current.getGeocodeAttempts()).isZero();
		assertThat(current.getLatitude()).isNull();
		assertThat(current.getLongitude()).isNull();
	}

	@Test
	void replaceAll_shouldResetFailedRowWhenGeocodeQueryIsAdded() {
		HouseRedevelopment current = existing("1460", "수송1-2", "수송동146-12");
		current.setGeocodeStatus(GeocodeStatus.FAILED);
		when(store.findAll()).thenReturn(List.of(current));
		RedevelopmentRow withQuery = new RedevelopmentRow("1460", "11110", "수송1-2", "도시정비형 재개발", "건축심의",
			"수송동146-12", "종로1길36", "서울특별시 종로구 종로 1");

		logic.replaceAll(List.of(withQuery), TODAY);

		assertThat(current.getGeocodeQuery()).isEqualTo("서울특별시 종로구 종로 1");
		assertThat(current.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
	}

	@Test
	void replaceAll_shouldDeleteRedevelopmentsMissingFromFileTogetherWithLinks() {
		when(store.findAll()).thenReturn(List.of(existing("1460", "수송1-2", "수송동146-12"),
			existing("9999", "철거구역", "고척동148")));

		RedevelopmentReplaceResult result = logic.replaceAll(List.of(row("1460", "수송1-2", "수송동146-12")), TODAY);

		verify(store).deleteWithLinks(List.of("id-9999"));
		assertThat(result.deleted()).isEqualTo(1);
		assertThat(result.total()).isEqualTo(1);
	}

	@Test
	void replaceAll_shouldRejectEmptyListToAvoidWipingEverything() {
		assertThatThrownBy(() -> logic.replaceAll(List.of(), TODAY)).isInstanceOf(IllegalArgumentException.class);
		verify(store, never()).deleteWithLinks(any());
	}

	@Test
	void applyGeocode_shouldMarkDoneWithCoordinates() {
		HouseRedevelopment target = existing("1460", "수송1-2", "수송동146-12");
		target.setGeocodeStatus(GeocodeStatus.PENDING);
		target.setGeocodeAttempts(1);

		logic.applyGeocode(target, GeocodeResult.found(37.6, 127.0, "서울특별시 종로구 종로1길 36"));

		assertThat(target.getGeocodeStatus()).isEqualTo(GeocodeStatus.DONE);
		assertThat(target.getLatitude()).isEqualTo(37.6);
		assertThat(target.getLongitude()).isEqualTo(127.0);
		assertThat(target.getGeocodeAttempts()).isZero();
	}

	@Test
	void applyGeocode_shouldFailImmediatelyWhenNotFound() {
		HouseRedevelopment target = existing("1460", "수송1-2", "수송동146-12");
		target.setGeocodeStatus(GeocodeStatus.PENDING);

		logic.applyGeocode(target, GeocodeResult.of(GeocodeResult.Outcome.NOT_FOUND));

		assertThat(target.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
	}

	@Test
	void applyGeocode_shouldCountUpstreamFailuresAndFailOnThird() {
		HouseRedevelopment target = existing("1460", "수송1-2", "수송동146-12");
		target.setGeocodeStatus(GeocodeStatus.PENDING);
		GeocodeResult upstream = GeocodeResult.of(GeocodeResult.Outcome.UPSTREAM_FAILED);

		logic.applyGeocode(target, upstream);
		logic.applyGeocode(target, upstream);
		assertThat(target.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
		assertThat(target.getGeocodeAttempts()).isEqualTo(2);

		logic.applyGeocode(target, upstream);
		assertThat(target.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
		assertThat(target.getGeocodeAttempts()).isEqualTo(3);
	}

	@Test
	void applyGeocode_shouldRejectOutcomesWithNothingToApply() {
		assertThatThrownBy(() -> logic.applyGeocode(existing("1", "a", "b"),
			GeocodeResult.of(GeocodeResult.Outcome.BUDGET_EXHAUSTED))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rebuildLinks_shouldReplaceLinksForGeocodedComplexesAndDoneRedevelopments() {
		HouseRedevelopment near = existing("1460", "수송1-2", "수송동146-12");
		near.setLatitude(37.5);
		near.setLongitude(127.0);
		HouseRedevelopment far = existing("1462", "먼구역", "수송동80");
		far.setLatitude(37.6);
		far.setLongitude(127.0);
		when(complexStore.findGeocodedPoints()).thenReturn(List.of(new ComplexPointPdo("C1", 37.5, 127.0)));
		when(store.findGeocoded()).thenReturn(List.of(near, far));
		when(store.replaceLinks(any())).thenReturn(1);

		RedevelopmentLinkResult result = logic.rebuildLinks();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Link>> links = ArgumentCaptor.forClass(List.class);
		verify(store).replaceLinks(links.capture());
		assertThat(links.getValue()).containsExactly(new Link("C1", "id-1460", 0));
		assertThat(result).isEqualTo(new RedevelopmentLinkResult(2, 1, 1));
	}

	@Test
	void rebuildLinks_shouldStillReplaceWithEmptyListWhenNothingIsNear() {
		when(complexStore.findGeocodedPoints()).thenReturn(new ArrayList<>());
		when(store.findGeocoded()).thenReturn(List.of());

		logic.rebuildLinks();

		verify(store).replaceLinks(List.of());
	}
}
