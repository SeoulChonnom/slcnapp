package com.seoulchonnom.aggregate.external.juso;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.aggregate.inspection.exception.AddressCoordinateNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;
import com.seoulchonnom.aggregate.inspection.geo.AddressCandidate;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * 행안부 도로명주소 검색 API(addrLinkApi.do)와 좌표제공 API(addrCoordApi.do) 어댑터.
 * 두 API는 승인키가 따로라서 각 호출이 자기 키만 확인한다(검색만 켜고 좌표는 꺼 둘 수 있다).
 *
 * 행안부는 오류도 HTTP 200으로 보내고 results.common.errorCode로 구분한다. 그래서 상태 코드가 아니라 errorCode를 본다.
 * 요청 URL에 승인키가 들어 있으므로 예외 메시지·cause는 로그와 응답에 내보내지 않는다.
 */
@Slf4j
public class JusoAddressGateway implements AddressGateway {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String SEARCH_PATH = "/addrlink/addrLinkApi.do";
	private static final String COORD_PATH = "/addrlink/addrCoordApi.do";

	/**
	 * errorCode 분류표. 한 곳에서만 관리한다.
	 * 확인(실제 응답): E0001 승인되지 않은 KEY, E0006 주소를 상세히 입력, E0008 검색어 두 글자 이상,
	 *   E0012 특수문자+숫자만, E0013 SQL 예약어·특수문자.
	 * 공식 문서·2차 자료 기준(미확인): E0005 검색어 미입력, E0009, E0014 개발승인키 만료, -999 시스템 에러.
	 * 표에 없는 코드는 상대 쪽 실패(502)로 본다.
	 */
	enum Category {
		/** 사용자가 검색어를 고치면 되는 경우 → 400 */
		KEYWORD,
		/** 키가 거절·만료되어 관리자가 고쳐야 하는 경우 → 503 */
		KEY_REJECTED,
		/** 그 밖의 상대 쪽 실패 → 502 */
		UPSTREAM;

		static Category of(String errorCode) {
			return switch (errorCode == null ? "" : errorCode) {
				case "E0005", "E0006", "E0008", "E0009", "E0012", "E0013" -> KEYWORD;
				case "E0001", "E0014" -> KEY_REJECTED;
				default -> UPSTREAM;
			};
		}
	}

	private final RestClient restClient;
	private final String searchKey;
	private final String coordKey;

	/**
	 * 타임아웃과 기본 URL은 RestClient를 만드는 쪽(JusoConfiguration)이 정한다.
	 */
	public JusoAddressGateway(RestClient restClient, String searchKey, String coordKey) {
		this.restClient = restClient;
		this.searchKey = searchKey == null ? "" : searchKey.trim();
		this.coordKey = coordKey == null ? "" : coordKey.trim();
	}

	@Override
	public AddressSearchResult search(String keyword, int page, int size) {
		if (searchKey.isEmpty()) {
			throw AddressLookupUnavailableException.notConfigured();
		}
		String body = get(uriBuilder -> uriBuilder.path(SEARCH_PATH)
			.queryParam("confmKey", "{confmKey}")
			.queryParam("currentPage", "{page}")
			.queryParam("countPerPage", "{size}")
			.queryParam("keyword", "{keyword}")
			.queryParam("resultType", "json")
			.build(searchKey, page, size, keyword));
		return parseSearch(body);
	}

	@Override
	public UtmkPoint findEntrance(CoordKey key) {
		if (coordKey.isEmpty()) {
			throw AddressLookupUnavailableException.notConfigured();
		}
		String body = get(uriBuilder -> uriBuilder.path(COORD_PATH)
			.queryParam("confmKey", "{confmKey}")
			.queryParam("admCd", "{admCd}")
			.queryParam("rnMgtSn", "{rnMgtSn}")
			.queryParam("udrtYn", "{udrtYn}")
			.queryParam("buldMnnm", "{buldMnnm}")
			.queryParam("buldSlno", "{buldSlno}")
			.queryParam("resultType", "json")
			.build(coordKey, key.admCd(), key.rnMgtSn(), key.udrtYn(), key.buldMnnm(), key.buldSlno()));
		return parseCoord(body);
	}

	private String get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
		try {
			String body = restClient.get().uri(uri).retrieve().body(String.class);
			if (body == null || body.isBlank()) {
				throw AddressLookupUnavailableException.upstreamFailed();
			}
			return body;
		} catch (AddressLookupUnavailableException e) {
			throw e;
		} catch (RuntimeException e) {
			// 메시지에 요청 URL(승인키 포함)이 들어가므로 클래스 이름만 남긴다.
			log.warn("행안부 주소 API 호출 실패: {}", e.getClass().getSimpleName());
			throw AddressLookupUnavailableException.upstreamFailed();
		}
	}

	AddressSearchResult parseSearch(String body) {
		JsonNode results = checkedResults(body);
		JsonNode common = results.path("common");
		long totalCount;
		try {
			totalCount = Long.parseLong(common.path("totalCount").asText());
		} catch (NumberFormatException e) {
			throw invalidBody();
		}
		List<AddressCandidate> candidates = new ArrayList<>();
		JsonNode juso = results.path("juso");
		if (juso.isArray()) {
			for (JsonNode node : juso) {
				candidates.add(new AddressCandidate(text(node, "roadAddr"), text(node, "jibunAddr"),
					text(node, "bdNm"), text(node, "bdMgtSn"), text(node, "zipNo"),
					new CoordKey(text(node, "admCd"), text(node, "rnMgtSn"), text(node, "udrtYn"),
						text(node, "buldMnnm"), text(node, "buldSlno"))));
			}
		}
		return new AddressSearchResult(totalCount, candidates);
	}

	UtmkPoint parseCoord(String body) {
		JsonNode juso = checkedResults(body).path("juso");
		if (!juso.isArray() || juso.isEmpty()) {
			// errorCode가 정상인데 결과가 없으면 장애가 아니라 좌표 정보가 없는 주소다.
			throw new AddressCoordinateNotFoundException();
		}
		try {
			return new UtmkPoint(Double.parseDouble(juso.get(0).path("entX").asText()),
				Double.parseDouble(juso.get(0).path("entY").asText()));
		} catch (NumberFormatException e) {
			throw invalidBody();
		}
	}

	/**
	 * 본문을 파싱하고 errorCode가 "0"이 아니면 분류표대로 예외를 던진다. 정상이면 results 노드를 돌려준다.
	 */
	private JsonNode checkedResults(String body) {
		JsonNode results;
		try {
			results = OBJECT_MAPPER.readTree(body).path("results");
		} catch (Exception e) {
			throw invalidBody();
		}
		JsonNode common = results.path("common");
		if (common.isMissingNode()) {
			throw invalidBody();
		}
		String errorCode = common.path("errorCode").asText(null);
		if ("0".equals(errorCode)) {
			return results;
		}
		switch (Category.of(errorCode)) {
			case KEYWORD -> throw new InvalidAddressKeywordException(common.path("errorMessage").asText(null));
			case KEY_REJECTED -> {
				log.warn("행안부 주소 API 승인키 오류: {}", errorCode);
				throw AddressLookupUnavailableException.notConfigured();
			}
			default -> {
				log.warn("행안부 주소 API 오류: {}", errorCode);
				throw AddressLookupUnavailableException.upstreamFailed();
			}
		}
	}

	private static AddressLookupUnavailableException invalidBody() {
		log.warn("행안부 주소 API 응답을 해석하지 못했습니다.");
		return AddressLookupUnavailableException.upstreamFailed();
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asText();
	}
}
