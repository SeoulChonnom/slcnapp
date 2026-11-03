package com.seoulchonnom.aggregate.external.datagokr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 공공데이터포털 JSON 응답을 읽는다. 정상 응답은 response.header/body(실거래·건축물대장) 또는
 * 최상위 header/body(학교) 두 형태이고, 게이트웨이 단계 오류는 OpenAPI_ServiceResponse.cmmMsgHeader로 온다.
 * 이 클래스는 어떤 것도 로그에 남기지 않는다. 본문과 URL에 키가 있을 수 있어서다.
 */
public final class DataGoKrResponseReader {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private DataGoKrResponseReader() {
	}

	/**
	 * @param result OK/NO_DATA면 items가 유효하다.
	 * @param totalCount 헤더가 알려 준 전체 건수. 없으면 이번 페이지 행 수
	 * @param items 이번 페이지의 행
	 */
	public record Page(DataGoKrResultCode result, int totalCount, List<JsonNode> items) {
		static Page of(DataGoKrResultCode result) {
			return new Page(result, 0, List.of());
		}
	}

	/**
	 * @param normalCodes 정상으로 보는 resultCode (실거래 "000", 건축물대장·학교 "00")
	 */
	public static Page read(String body, Set<String> normalCodes) {
		if (body == null || body.isBlank()) {
			return Page.of(DataGoKrResultCode.UPSTREAM);
		}
		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(body);
		} catch (Exception e) {
			// JSON이 아니어도 게이트웨이가 XML로 키 오류를 보낼 수 있다. 오류 이름만 찾아 분류한다.
			return Page.of(DataGoKrResultCode.classify(body));
		}
		if (root == null || !root.isObject()) {
			return Page.of(DataGoKrResultCode.UPSTREAM);
		}

		JsonNode gatewayError = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
		if (!gatewayError.isMissingNode()) {
			DataGoKrResultCode byMessage = DataGoKrResultCode.classify(text(gatewayError, "errMsg"));
			if (byMessage != DataGoKrResultCode.UPSTREAM) {
				return Page.of(byMessage);
			}
			return Page.of(DataGoKrResultCode.classifyReasonCode(text(gatewayError, "returnReasonCode")));
		}

		JsonNode envelope = root.has("response") ? root.path("response") : root;
		JsonNode header = envelope.path("header");
		if (header.isMissingNode()) {
			return Page.of(DataGoKrResultCode.UPSTREAM);
		}
		String resultCode = text(header, "resultCode");
		if (resultCode == null || !normalCodes.contains(resultCode)) {
			DataGoKrResultCode code = DataGoKrResultCode.classify(text(header, "resultMsg"));
			return Page.of(code == DataGoKrResultCode.OK ? DataGoKrResultCode.UPSTREAM : code);
		}

		JsonNode bodyNode = envelope.path("body");
		List<JsonNode> items = items(bodyNode.path("items"));
		Integer totalCount = intOrNull(text(bodyNode, "totalCount"));
		return new Page(DataGoKrResultCode.OK, totalCount == null ? items.size() : totalCount, items);
	}

	/**
	 * 값을 asText로 읽고 trim한다. 빈 값은 null이 아니라 공백 한 칸(" ")으로 오므로 빈 문자열이면 null로 본다.
	 * 같은 필드가 행마다 숫자로도 문자열로도 와서 타입에 기대지 않는다.
	 */
	public static String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asText().strip();
		return text.isEmpty() ? null : text;
	}

	/**
	 * items는 {"item":[...]}, {"item":{...}}(1건), 빈 문자열(0건), 배열 직접 형태로 올 수 있다.
	 */
	private static List<JsonNode> items(JsonNode items) {
		List<JsonNode> result = new ArrayList<>();
		JsonNode rows = items;
		if (items.isObject()) {
			rows = items.path("item");
		}
		if (rows.isArray()) {
			rows.forEach(result::add);
		} else if (rows.isObject()) {
			result.add(rows);
		}
		return result;
	}

	private static Integer intOrNull(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
