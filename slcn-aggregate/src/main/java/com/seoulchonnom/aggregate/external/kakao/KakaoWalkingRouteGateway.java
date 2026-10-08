package com.seoulchonnom.aggregate.external.kakao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.aggregate.inspection.exception.WalkingRouteException;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteSegment;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오맵 REST 도보 경로 조회(GET /v2/routing/walk) 어댑터.
 *
 * 외부로는 좌표(start/end/via)만 보낸다. 이름(s_name 등)과 임장·사용자 식별자는 보내지 않고 route_mode도 생략한다.
 *
 * 카카오는 401 본문에 REST 키를 그대로 되돌려 준다(wrong appKey(키) format).
 * 그래서 응답 본문과 RestClient 예외의 메시지·cause는 로그와 응답에 절대 내보내지 않고,
 * 상태 코드와 예외 클래스 이름, 카카오 status 문자열만 남긴다.
 */
@Slf4j
public class KakaoWalkingRouteGateway implements WalkingRouteGateway {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String PATH = "/v2/routing/walk";
	private static final int MIN_POINTS = 2;
	private static final int MAX_POINTS = 7;

	private final RestClient restClient;
	private final String restKey;

	/**
	 * 타임아웃과 기본 URL은 RestClient를 만드는 쪽(KakaoConfiguration)이 정한다.
	 */
	public KakaoWalkingRouteGateway(RestClient restClient, String restKey) {
		this.restClient = restClient;
		this.restKey = restKey == null ? "" : restKey.trim();
	}

	@Override
	public WalkingRouteSegment route(List<GeoPoint> points) {
		if (restKey.isEmpty()) {
			throw WalkingRouteException.notConfigured();
		}
		if (points == null || points.size() < MIN_POINTS || points.size() > MAX_POINTS) {
			throw new IllegalArgumentException("도보 경로 지점은 2~7개여야 합니다.");
		}
		GeoPoint start = points.get(0);
		GeoPoint end = points.get(points.size() - 1);
		List<GeoPoint> via = points.subList(1, points.size() - 1);
		String body = get(start, end, via);
		return parse(body, points.size() - 1);
	}

	private String get(GeoPoint start, GeoPoint end, List<GeoPoint> via) {
		Function<org.springframework.web.util.UriBuilder, java.net.URI> uri = builder -> {
			builder.path(PATH)
				.queryParam("start_x", "{sx}")
				.queryParam("start_y", "{sy}")
				.queryParam("end_x", "{ex}")
				.queryParam("end_y", "{ey}");
			if (via.isEmpty()) {
				return builder.build(plain(start.longitude()), plain(start.latitude()), plain(end.longitude()),
					plain(end.latitude()));
			}
			// 경유지 값은 숫자와 쉼표뿐이라 템플릿 없이 그대로 넣는다(쉼표를 %2C로 바꾸지 않는다).
			builder.queryParam("via_x", via.stream().map(p -> plain(p.longitude())).collect(Collectors.joining(",")))
				.queryParam("via_y", via.stream().map(p -> plain(p.latitude())).collect(Collectors.joining(",")));
			return builder.build(plain(start.longitude()), plain(start.latitude()), plain(end.longitude()),
				plain(end.latitude()));
		};
		try {
			String body = restClient.get()
				.uri(uri)
				.header("Authorization", "KakaoAK " + restKey)
				.retrieve()
				.body(String.class);
			if (body == null || body.isBlank()) {
				throw WalkingRouteException.upstreamFailed();
			}
			return body;
		} catch (WalkingRouteException e) {
			throw e;
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status == 401 || status == 403) {
				log.warn("카카오 도보 경로 API 키 거절: status={}", status);
				throw WalkingRouteException.notConfigured();
			}
			if (status == 429) {
				log.warn("카카오 도보 경로 API 한도 초과: status={}", status);
				throw WalkingRouteException.quotaExceeded();
			}
			log.warn("카카오 도보 경로 API 호출 실패: status={}", status);
			throw WalkingRouteException.upstreamFailed();
		} catch (RuntimeException e) {
			// 메시지에 요청 URL이나 응답 본문(키 포함)이 들어갈 수 있어 클래스 이름만 남긴다.
			log.warn("카카오 도보 경로 API 호출 실패: {}", e.getClass().getSimpleName());
			throw WalkingRouteException.upstreamFailed();
		}
	}

	WalkingRouteSegment parse(String body, int expectedLegs) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(body);
			String status = root.path("status").asText("");
			if (!"OK".equals(status)) {
				// status는 SAME_POINT 같은 고정 문자열이라 남겨도 안전하다.
				log.warn("카카오 도보 경로 결과 없음: status={}", status);
				throw WalkingRouteException.upstreamFailed();
			}
			JsonNode route = root.path("route");
			JsonNode properties = route.path("properties");
			int totalDistance = requiredInt(properties, "totalDistance");
			int totalTime = requiredInt(properties, "totalTime");
			JsonNode legNodes = route.path("legs");
			if (!legNodes.isArray() || legNodes.size() != expectedLegs) {
				return invalid();
			}
			List<WalkingRouteSegment.Leg> legs = new ArrayList<>();
			for (JsonNode legNode : legNodes) {
				JsonNode legProperties = legNode.path("properties");
				legs.add(new WalkingRouteSegment.Leg(requiredInt(legProperties, "distance"),
					requiredInt(legProperties, "time"), path(legNode.path("steps"))));
			}
			return new WalkingRouteSegment(totalDistance, totalTime, legs);
		} catch (WalkingRouteException e) {
			throw e;
		} catch (Exception e) {
			return invalid();
		}
	}

	/**
	 * 구간의 steps를 이어 붙인다. 앞 step의 끝점과 다음 step의 시작점이 같으면 한 번만 넣는다.
	 */
	private static List<GeoPoint> path(JsonNode steps) {
		if (!steps.isArray()) {
			throw new IllegalStateException();
		}
		List<GeoPoint> path = new ArrayList<>();
		for (JsonNode step : steps) {
			JsonNode points = step.path("path").path("points");
			if (!points.isArray()) {
				throw new IllegalStateException();
			}
			for (JsonNode xy : points) {
				if (!xy.isArray() || xy.size() < 2 || !xy.get(0).isNumber() || !xy.get(1).isNumber()) {
					throw new IllegalStateException();
				}
				GeoPoint point = new GeoPoint(xy.get(1).asDouble(), xy.get(0).asDouble());
				if (path.isEmpty() || !path.get(path.size() - 1).equals(point)) {
					path.add(point);
				}
			}
		}
		return path;
	}

	private static int requiredInt(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isNumber()) {
			throw new IllegalStateException();
		}
		return value.asInt();
	}

	private static WalkingRouteSegment invalid() {
		log.warn("카카오 도보 경로 API 응답을 해석하지 못했습니다.");
		throw WalkingRouteException.upstreamFailed();
	}

	/** 지수 표기 없이 소수 그대로 보낸다. */
	private static String plain(double value) {
		return BigDecimal.valueOf(value).toPlainString();
	}
}
