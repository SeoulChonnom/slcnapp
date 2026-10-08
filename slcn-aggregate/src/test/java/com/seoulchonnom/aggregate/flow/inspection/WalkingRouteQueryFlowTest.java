package com.seoulchonnom.aggregate.flow.inspection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteSegment;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;
import com.seoulchonnom.spec.inspection.facade.sdo.WalkingRouteRdo;

class WalkingRouteQueryFlowTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";

	private final InspectionVisitStore visitStore = mock(InspectionVisitStore.class);
	private final ViewedPropertyStore propertyStore = mock(ViewedPropertyStore.class);
	private final WalkingRouteGateway gateway = mock(WalkingRouteGateway.class);
	private final WalkingRouteQueryFlow flow = new WalkingRouteQueryFlow(visitStore, propertyStore, gateway);

	private static ViewedProperty property(String id, Double lat, Double lon) {
		ViewedProperty property = new ViewedProperty(VISIT_ID, "단지", "101호", 0);
		property.setId(id);
		if (lat != null) {
			property.changeLocation(new PropertyLocation("bd-" + id, "주소", lat, lon, 1.0, 2.0));
		}
		return property;
	}

	/** 지점 i는 위도 37 + i * 0.01, 경도 127 + i * 0.01로 서로 다르다. */
	private static List<ViewedProperty> distinctProperties(int count) {
		return IntStream.range(0, count)
			.mapToObj(i -> property("P" + i, 37 + i * 0.01, 127 + i * 0.01))
			.toList();
	}

	/** 지점마다 거리 100, 시간 60인 구간을 지점 수 - 1개 돌려주는 대역. */
	private void stubGateway() {
		when(gateway.route(anyList())).thenAnswer(invocation -> {
			List<GeoPoint> points = invocation.getArgument(0);
			List<WalkingRouteSegment.Leg> legs = new ArrayList<>();
			for (int i = 0; i < points.size() - 1; i++) {
				legs.add(new WalkingRouteSegment.Leg(i, i + 1, 100, 60, List.of(points.get(i), points.get(i + 1))));
			}
			return new WalkingRouteSegment(100 * legs.size(), 60 * legs.size(), legs);
		});
	}

	private List<List<GeoPoint>> capturedCalls() {
		ArgumentCaptor<List<GeoPoint>> captor = ArgumentCaptor.forClass(List.class);
		verify(gateway, atLeast(0)).route(captor.capture());
		return captor.getAllValues();
	}

	@Test
	void getWalkingRoute_shouldChunkWithOverlapAndCountCalls() {
		int[][] cases = {{2, 1}, {7, 1}, {8, 2}, {13, 2}, {14, 3}};
		for (int[] testCase : cases) {
			reset(gateway, propertyStore);
			stubGateway();
			when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(distinctProperties(testCase[0]));

			WalkingRouteRdo rdo = flow.getWalkingRoute(VISIT_ID);

			assertThat(capturedCalls()).as("stops=%d", testCase[0]).hasSize(testCase[1]);
			assertThat(rdo.getStops()).hasSize(testCase[0]);
			assertThat(rdo.getLegs()).hasSize(testCase[0] - 1);
		}
	}

	@Test
	void getWalkingRoute_shouldStartNextChunkAtPreviousChunkEnd() {
		stubGateway();
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(distinctProperties(14));

		flow.getWalkingRoute(VISIT_ID);

		List<List<GeoPoint>> calls = capturedCalls();
		// stops[0..6], stops[6..12], stops[12..13]
		assertThat(calls.get(0)).hasSize(7).first().isEqualTo(new GeoPoint(37.0, 127.0));
		assertThat(calls.get(0).get(6)).isEqualTo(new GeoPoint(37 + 6 * 0.01, 127 + 6 * 0.01));
		assertThat(calls.get(1)).hasSize(7).first().isEqualTo(calls.get(0).get(6));
		assertThat(calls.get(1).get(6)).isEqualTo(new GeoPoint(37 + 12 * 0.01, 127 + 12 * 0.01));
		assertThat(calls.get(2)).hasSize(2).first().isEqualTo(calls.get(1).get(6));
		assertThat(calls.get(2).get(1)).isEqualTo(new GeoPoint(37 + 13 * 0.01, 127 + 13 * 0.01));
	}

	@Test
	void getWalkingRoute_shouldSumTotalsAndLabelLegsAcrossChunks() {
		stubGateway();
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(distinctProperties(9));

		WalkingRouteRdo rdo = flow.getWalkingRoute(VISIT_ID);

		assertThat(rdo.getTotalDistance()).isEqualTo(800);
		assertThat(rdo.getTotalTime()).isEqualTo(480);
		for (int i = 0; i < 8; i++) {
			assertThat(rdo.getLegs().get(i).getFromStopIndex()).isEqualTo(i);
			assertThat(rdo.getLegs().get(i).getToStopIndex()).isEqualTo(i + 1);
		}
		// 경로는 [경도, 위도] 순서다
		assertThat(rdo.getLegs().get(7).getPath().get(0)).containsExactly(127 + 7 * 0.01, 37 + 7 * 0.01);
		assertThat(rdo.getStops().get(3).getIndex()).isEqualTo(3);
		assertThat(rdo.getStops().get(3).getPropertyIds()).containsExactly("P3");
	}

	@Test
	void getWalkingRoute_shouldSkipUnlocatedAndMergeConsecutiveSameCoordinates() {
		stubGateway();
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of(
			property("A1", 37.5, 127.0), property("NONE", null, null), property("A2", 37.5, 127.0),
			property("B", 37.6, 127.1), property("C", 37.7, 127.2)));

		WalkingRouteRdo rdo = flow.getWalkingRoute(VISIT_ID);

		assertThat(rdo.getStops()).hasSize(3);
		assertThat(rdo.getStops().get(0).getPropertyIds()).containsExactly("A1", "A2");
		assertThat(rdo.getStops().get(1).getPropertyIds()).containsExactly("B");
		assertThat(rdo.getStops().stream().flatMap(s -> s.getPropertyIds().stream())).doesNotContain("NONE");
		assertThat(rdo.getLegs()).hasSize(2);
	}

	@Test
	void getWalkingRoute_shouldKeepNonConsecutiveRepeatsSeparate() {
		stubGateway();
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of(
			property("A1", 37.5, 127.0), property("B", 37.6, 127.1), property("A2", 37.5, 127.0)));

		WalkingRouteRdo rdo = flow.getWalkingRoute(VISIT_ID);

		assertThat(rdo.getStops()).hasSize(3);
		assertThat(rdo.getStops().get(2).getPropertyIds()).containsExactly("A2");
		assertThat(rdo.getLegs()).hasSize(2);
	}

	@Test
	void getWalkingRoute_shouldReturnEmptyRouteWithoutCallingGatewayWhenFewerThanTwoStops() {
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of());
		assertEmpty(flow.getWalkingRoute(VISIT_ID));

		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of(property("A", 37.5, 127.0)));
		assertEmpty(flow.getWalkingRoute(VISIT_ID));

		// 같은 좌표만 있으면 합쳐져 한 지점이다
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of(
			property("A", 37.5, 127.0), property("B", 37.5, 127.0), property("C", null, null)));
		assertEmpty(flow.getWalkingRoute(VISIT_ID));

		verifyNoInteractions(gateway);
	}

	@Test
	void getWalkingRoute_shouldFailWholeRequestWhenAnyChunkFails() {
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(distinctProperties(10));
		when(gateway.route(anyList()))
			.thenReturn(new WalkingRouteSegment(600, 360, IntStream.range(0, 6)
				.mapToObj(i -> new WalkingRouteSegment.Leg(i, i + 1, 100, 60, List.of(new GeoPoint(37, 127)))).toList()))
			.thenThrow(WalkingRouteException.quotaExceeded());

		assertThatThrownBy(() -> flow.getWalkingRoute(VISIT_ID)).isInstanceOfSatisfying(WalkingRouteException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WALKING_ROUTE_QUOTA_EXCEEDED));
	}

	@Test
	void getWalkingRoute_shouldPropagateVisitNotFoundWithoutLoadingProperties() {
		when(visitStore.findById("none")).thenThrow(new InspectionVisitNotFoundException());

		assertThatThrownBy(() -> flow.getWalkingRoute("none")).isInstanceOf(InspectionVisitNotFoundException.class);
		verifyNoInteractions(propertyStore, gateway);
	}

	private static void assertEmpty(WalkingRouteRdo rdo) {
		assertThat(rdo.getTotalDistance()).isZero();
		assertThat(rdo.getTotalTime()).isZero();
		assertThat(rdo.getStops()).isEmpty();
		assertThat(rdo.getLegs()).isEmpty();
	}

	@Test
	void getWalkingRoute_shouldEmitSpanningLegWithChunkOffsetAndKeepTotals() {
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(distinctProperties(9));
		// 첫 묶음(0..6)은 묶음 전체를 잇는 구간 하나, 둘째 묶음(6..8)은 정상 구간 둘.
		when(gateway.route(anyList()))
			.thenReturn(new WalkingRouteSegment(600, 360,
				List.of(new WalkingRouteSegment.Leg(0, 6, 600, 360, List.of(new GeoPoint(37, 127))))))
			.thenReturn(new WalkingRouteSegment(200, 120, List.of(
				new WalkingRouteSegment.Leg(0, 1, 100, 60, List.of()),
				new WalkingRouteSegment.Leg(1, 2, 100, 60, List.of()))));

		WalkingRouteRdo rdo = flow.getWalkingRoute(VISIT_ID);

		assertThat(rdo.getTotalDistance()).isEqualTo(800);
		assertThat(rdo.getTotalTime()).isEqualTo(480);
		assertThat(rdo.getLegs()).hasSize(3);
		assertThat(rdo.getLegs().get(0).getFromStopIndex()).isZero();
		assertThat(rdo.getLegs().get(0).getToStopIndex()).isEqualTo(6);
		assertThat(rdo.getLegs().get(1).getFromStopIndex()).isEqualTo(6);
		assertThat(rdo.getLegs().get(1).getToStopIndex()).isEqualTo(7);
		assertThat(rdo.getLegs().get(2).getToStopIndex()).isEqualTo(8);
	}
}
