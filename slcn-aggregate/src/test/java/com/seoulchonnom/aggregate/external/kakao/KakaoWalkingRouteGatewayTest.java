package com.seoulchonnom.aggregate.external.kakao;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;
import com.seoulchonnom.aggregate.inspection.geo.DisabledWalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteSegment;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class KakaoWalkingRouteGatewayTest {
	private static final String BASE = "https://kakao.test";
	private static final String SECRET = "SECRET-REST-KEY-1234";
	private static final GeoPoint A = new GeoPoint(37.5, 127.0);
	private static final GeoPoint B = new GeoPoint(37.51, 127.01);
	private static final GeoPoint C = new GeoPoint(37.52, 127.02);

	private MockRestServiceServer server;
	private KakaoWalkingRouteGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new KakaoWalkingRouteGateway(builder.build(), SECRET);
	}

	private static final String OK_TWO_LEGS = """
		{"status":"OK","route":{"properties":{"totalDistance":900,"totalTime":700,"landingUrl":"https://map.kakao.com/x"},
		"legs":[
		 {"properties":{"distance":400,"time":300},"steps":[
		   {"properties":{"distance":200,"guidance":"a","time":150,"x":127.0,"y":37.5},"path":{"points":[[127.0,37.5],[127.001,37.501]]}},
		   {"properties":{"distance":200,"guidance":"b","time":150,"x":127.001,"y":37.501},"path":{"points":[[127.001,37.501],[127.01,37.51]]}}]},
		 {"properties":{"distance":500,"time":400},"steps":[
		   {"properties":{"distance":500,"guidance":"c","time":400,"x":127.01,"y":37.51},"path":{"points":[[127.01,37.51],[127.02,37.52]]}}]}
		]}}""";

	private void failWith(HttpStatus status, String body) {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
	}

	private void assertFailure(ErrorCode expected) {
		assertThatThrownBy(() -> gateway.route(List.of(A, C))).isInstanceOfSatisfying(WalkingRouteException.class,
			e -> {
				assertThat(e.getErrorCode()).isEqualTo(expected);
				assertNoSecret(e);
			});
	}

	private static void assertNoSecret(Throwable e) {
		assertThat(e.getCause()).isNull();
		assertThat(e.getMessage()).doesNotContain(SECRET);
		assertThat(e.toString()).doesNotContain(SECRET);
		assertThat(e.getSuppressed()).isEmpty();
	}

	@Test
	void route_shouldSendOnlyCoordinatesAndAuthorizationHeader() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v2/routing/walk?")))
			.andExpect(header("Authorization", "KakaoAK " + SECRET))
			.andExpect(queryParam("start_x", "127.0"))
			.andExpect(queryParam("start_y", "37.5"))
			.andExpect(queryParam("end_x", "127.02"))
			.andExpect(queryParam("end_y", "37.52"))
			.andExpect(queryParam("via_x", "127.01"))
			.andExpect(queryParam("via_y", "37.51"))
			.andRespond(withSuccess(OK_TWO_LEGS, MediaType.APPLICATION_JSON));

		gateway.route(List.of(A, B, C));

		server.verify();
	}

	@Test
	void route_shouldOmitViaWhenTwoPointsAndSendNoOtherParams() {
		server.expect(requestTo(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith(BASE + "/v2/routing/walk?"),
			org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("via_")),
			org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("_name")),
			org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("route_mode")))))
			.andRespond(withSuccess("""
				{"status":"OK","route":{"properties":{"totalDistance":1,"totalTime":2,"landingUrl":"u"},
				"legs":[{"properties":{"distance":1,"time":2},"steps":[]}]}}""", MediaType.APPLICATION_JSON));

		WalkingRouteSegment segment = gateway.route(List.of(A, C));

		assertThat(segment.legs()).hasSize(1);
		assertThat(segment.legs().get(0).path()).isEmpty();
		server.verify();
	}

	@Test
	void route_shouldSendViaCommaSeparatedInOrder() {
		GeoPoint d = new GeoPoint(37.53, 127.03);
		server.expect(queryParam("via_x", "127.01,127.02"))
			.andExpect(queryParam("via_y", "37.51,37.52"))
			.andRespond(withSuccess("""
				{"status":"OK","route":{"properties":{"totalDistance":3,"totalTime":3,"landingUrl":"u"},"legs":[
				{"properties":{"distance":1,"time":1},"steps":[]},{"properties":{"distance":1,"time":1},"steps":[]},
				{"properties":{"distance":1,"time":1},"steps":[]}]}}""", MediaType.APPLICATION_JSON));

		assertThat(gateway.route(List.of(A, B, C, d)).legs()).hasSize(3);
		server.verify();
	}

	@Test
	void route_shouldParseTotalsAndConcatenateStepsDroppingSharedEndpoint() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(withSuccess(OK_TWO_LEGS, MediaType.APPLICATION_JSON));

		WalkingRouteSegment segment = gateway.route(List.of(A, B, C));

		assertThat(segment.totalDistance()).isEqualTo(900);
		assertThat(segment.totalTime()).isEqualTo(700);
		assertThat(segment.legs()).hasSize(2);
		assertThat(segment.legs().get(0).distance()).isEqualTo(400);
		assertThat(segment.legs().get(0).time()).isEqualTo(300);
		// 두 step이 [127.001,37.501]을 공유하므로 3점이다. 위도 경도 순서의 GeoPoint로 담긴다.
		assertThat(segment.legs().get(0).path()).containsExactly(new GeoPoint(37.5, 127.0),
			new GeoPoint(37.501, 127.001), new GeoPoint(37.51, 127.01));
		assertThat(segment.legs().get(1).path()).hasSize(2);
	}

	@Test
	void route_shouldMapAuthFailureTo503WithoutLeakingKeyFromBody() {
		failWith(HttpStatus.UNAUTHORIZED,
			"{\"errorType\":\"AccessDeniedError\",\"message\":\"wrong appKey(" + SECRET + ") format\"}");
		assertFailure(ErrorCode.WALKING_ROUTE_UNAVAILABLE);

		server.reset();
		failWith(HttpStatus.FORBIDDEN, "{\"message\":\"" + SECRET + "\"}");
		assertFailure(ErrorCode.WALKING_ROUTE_UNAVAILABLE);
	}

	@Test
	void route_shouldMapQuotaTo429() {
		failWith(HttpStatus.TOO_MANY_REQUESTS, "{\"errorType\":\"QuotaExceeded\",\"message\":\"" + SECRET + "\"}");
		assertFailure(ErrorCode.WALKING_ROUTE_QUOTA_EXCEEDED);
		assertThat(ErrorCode.WALKING_ROUTE_QUOTA_EXCEEDED.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void route_shouldMapOtherHttpFailuresTo502() {
		failWith(HttpStatus.INTERNAL_SERVER_ERROR, SECRET);
		assertFailure(ErrorCode.WALKING_ROUTE_FAILED);
		server.reset();
		failWith(HttpStatus.BAD_REQUEST, "{\"message\":\"" + SECRET + "\"}");
		assertFailure(ErrorCode.WALKING_ROUTE_FAILED);
	}

	@Test
	void route_shouldMapNonOkStatusTo502() {
		for (String status : List.of("SAME_POINT", "START_LINK_NOT_FOUND", "END_LINK_NOT_FOUND",
			"TOO_MANY_SEARCH_LINK", "TOO_FAR_AWAY", "ROUTE_RESULT_NOT_FOUND")) {
			server.reset();
			server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
				.andRespond(withSuccess("{\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
			assertFailure(ErrorCode.WALKING_ROUTE_FAILED);
		}
	}

	@Test
	void route_shouldMapTimeoutAndIoErrorsTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(request -> {
				throw new SocketTimeoutException("Read timed out " + SECRET);
			});
		assertFailure(ErrorCode.WALKING_ROUTE_FAILED);

		server.reset();
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(request -> {
				throw new IOException("connection reset " + SECRET);
			});
		assertFailure(ErrorCode.WALKING_ROUTE_FAILED);
	}

	@Test
	void route_shouldMapUnparsableOrMismatchedBodyTo502() {
		for (String body : List.of("not json", "{}", "{\"status\":\"OK\"}",
			"{\"status\":\"OK\",\"route\":{\"properties\":{\"totalDistance\":1,\"totalTime\":1},\"legs\":[]}}")) {
			server.reset();
			server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
			assertFailure(ErrorCode.WALKING_ROUTE_FAILED);
		}
	}

	@Test
	void route_shouldReturn503WithoutCallingWhenKeyIsBlank() {
		KakaoWalkingRouteGateway blank = new KakaoWalkingRouteGateway(RestClient.builder().baseUrl(BASE).build(), "  ");

		assertThatThrownBy(() -> blank.route(List.of(A, C))).isInstanceOfSatisfying(WalkingRouteException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WALKING_ROUTE_UNAVAILABLE));
	}

	@Test
	void route_shouldRejectPointCountOutsideTwoToSeven() {
		assertThatThrownBy(() -> gateway.route(List.of(A))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gateway.route(java.util.Collections.nCopies(8, A)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void disabledGateway_shouldAlwaysReturn503() {
		assertThatThrownBy(() -> new DisabledWalkingRouteGateway().route(List.of(A, C)))
			.isInstanceOfSatisfying(WalkingRouteException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WALKING_ROUTE_UNAVAILABLE));
	}
}
