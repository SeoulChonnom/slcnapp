package com.seoulchonnom.rest.inspection;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.flow.inspection.InspectionReviewSuggestionFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitFlow;
import com.seoulchonnom.aggregate.flow.inspection.InspectionVisitQueryFlow;
import com.seoulchonnom.aggregate.flow.inspection.ViewedPropertyFlow;
import com.seoulchonnom.aggregate.flow.inspection.WalkingRouteQueryFlow;
import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteSegment;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;

/**
 * 저장소와 게이트웨이만 대역으로 두고 Flow까지 실제로 태워, 응답 JSON과 오류 상태가 계약대로인지 본다.
 */
class WalkingRouteResourceTest {
	private static final String VISIT_ID = "INSPECTION_VISIT-0001";

	private InspectionVisitStore visitStore;
	private ViewedPropertyStore propertyStore;
	private WalkingRouteGateway gateway;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		visitStore = mock(InspectionVisitStore.class);
		propertyStore = mock(ViewedPropertyStore.class);
		gateway = mock(WalkingRouteGateway.class);
		InspectionVisitResource resource = new InspectionVisitResource(mock(InspectionVisitQueryFlow.class),
			mock(InspectionVisitFlow.class), mock(ViewedPropertyFlow.class),
			mock(InspectionReviewSuggestionFlow.class), new WalkingRouteQueryFlow(visitStore, propertyStore, gateway));
		mockMvc = MockMvcBuilders.standaloneSetup(resource).setControllerAdvice(new CommonExceptionHandler()).build();
	}

	private static ViewedProperty property(String id, double lat, double lon) {
		ViewedProperty property = new ViewedProperty(VISIT_ID, "단지", "101호", 0);
		property.setId(id);
		property.changeLocation(new PropertyLocation("bd-" + id, "주소", lat, lon, 1.0, 2.0));
		return property;
	}

	@Test
	void walkingRoute_shouldReturnStopsAndLegs() throws Exception {
		when(propertyStore.findAllByVisitId(VISIT_ID))
			.thenReturn(List.of(property("P1", 37.5, 127.0), property("P2", 37.6, 127.1)));
		when(gateway.route(anyList())).thenReturn(new WalkingRouteSegment(400, 300, List.of(
			new WalkingRouteSegment.Leg(400, 300, List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.1))))));

		mockMvc.perform(post("/inspection-visits/{id}/walking-route", VISIT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalDistance").value(400))
			.andExpect(jsonPath("$.totalTime").value(300))
			.andExpect(jsonPath("$.stops[0].index").value(0))
			.andExpect(jsonPath("$.stops[0].propertyIds[0]").value("P1"))
			.andExpect(jsonPath("$.stops[1].latitude").value(37.6))
			.andExpect(jsonPath("$.legs[0].fromStopIndex").value(0))
			.andExpect(jsonPath("$.legs[0].toStopIndex").value(1))
			.andExpect(jsonPath("$.legs[0].path[0][0]").value(127.0))
			.andExpect(jsonPath("$.legs[0].path[0][1]").value(37.5));
	}

	@Test
	void walkingRoute_shouldReturnEmptyRouteForOneStop() throws Exception {
		when(propertyStore.findAllByVisitId(VISIT_ID)).thenReturn(List.of(property("P1", 37.5, 127.0)));

		mockMvc.perform(post("/inspection-visits/{id}/walking-route", VISIT_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalDistance").value(0))
			.andExpect(jsonPath("$.stops").isEmpty())
			.andExpect(jsonPath("$.legs").isEmpty());
		verifyNoInteractions(gateway);
	}

	@Test
	void walkingRoute_shouldReturnNotFoundCodeForUnknownVisit() throws Exception {
		when(visitStore.findById("none")).thenThrow(new InspectionVisitNotFoundException());

		mockMvc.perform(post("/inspection-visits/{id}/walking-route", "none"))
			.andExpect(jsonPath("$.code").value("INSPECTION_VISIT_NOT_FOUND"));
	}

	@Test
	void walkingRoute_shouldMapGatewayErrors() throws Exception {
		when(propertyStore.findAllByVisitId(VISIT_ID))
			.thenReturn(List.of(property("P1", 37.5, 127.0), property("P2", 37.6, 127.1)));

		doThrow(WalkingRouteException.quotaExceeded()).when(gateway).route(anyList());
		mockMvc.perform(post("/inspection-visits/{id}/walking-route", VISIT_ID))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("WALKING_ROUTE_QUOTA_EXCEEDED"));

		doThrow(WalkingRouteException.upstreamFailed()).when(gateway).route(anyList());
		mockMvc.perform(post("/inspection-visits/{id}/walking-route", VISIT_ID))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("WALKING_ROUTE_FAILED"));

		doThrow(WalkingRouteException.notConfigured()).when(gateway).route(anyList());
		mockMvc.perform(post("/inspection-visits/{id}/walking-route", VISIT_ID))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("WALKING_ROUTE_UNAVAILABLE"));
	}
}
