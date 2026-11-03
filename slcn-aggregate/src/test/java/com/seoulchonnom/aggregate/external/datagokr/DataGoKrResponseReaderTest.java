package com.seoulchonnom.aggregate.external.datagokr;

import static com.seoulchonnom.aggregate.external.datagokr.TradeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DataGoKrResponseReaderTest {
	private static final Set<String> TRADE = Set.of("000");
	private static final Set<String> REGISTER = Set.of("00");

	@Test
	void read_shouldReadNormalTradeEnvelope() {
		var page = DataGoKrResponseReader.read(THREE_ROWS, TRADE);

		assertThat(page.result()).isEqualTo(DataGoKrResultCode.OK);
		assertThat(page.totalCount()).isEqualTo(116);
		assertThat(page.items()).hasSize(3);
	}

	@Test
	void read_shouldReadTopLevelHeaderBodyEnvelopeWithCode00() {
		String body = "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},"
			+ "\"body\":{\"items\":[{\"schoolNm\":\"가나초등학교\"}],\"totalCount\":\"1\"}}";

		var page = DataGoKrResponseReader.read(body, REGISTER);

		assertThat(page.result()).isEqualTo(DataGoKrResultCode.OK);
		assertThat(page.items()).hasSize(1);
		assertThat(page.totalCount()).isEqualTo(1);
	}

	@Test
	void read_shouldTreatCode00AsErrorForTradeOnlyWhenNotListedNormal() {
		String body = THREE_ROWS.replace("\"000\"", "\"00\"");

		assertThat(DataGoKrResponseReader.read(body, TRADE).result()).isEqualTo(DataGoKrResultCode.UPSTREAM);
		assertThat(DataGoKrResponseReader.read(body, Set.of("000", "00")).result()).isEqualTo(DataGoKrResultCode.OK);
	}

	@Test
	void read_shouldHandleSingleItemObject() {
		var page = DataGoKrResponseReader.read(SINGLE_ROW_AS_OBJECT, TRADE);

		assertThat(page.items()).hasSize(1);
		assertThat(page.totalCount()).isEqualTo(1);
	}

	@Test
	void read_shouldHandleEmptyStringItems() {
		var page = DataGoKrResponseReader.read(EMPTY_ITEMS, TRADE);

		assertThat(page.result()).isEqualTo(DataGoKrResultCode.OK);
		assertThat(page.items()).isEmpty();
		assertThat(page.totalCount()).isZero();
	}

	@Test
	void read_shouldClassifyGatewayErrorEnvelope() {
		assertThat(DataGoKrResponseReader.read(GATEWAY_TIMEOUT, TRADE).result()).isEqualTo(DataGoKrResultCode.UPSTREAM);
		assertThat(DataGoKrResponseReader.read(gatewayError("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "30"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.KEY_REJECTED);
		assertThat(DataGoKrResponseReader.read(gatewayError("SERVICE_ACCESS_DENIED_ERROR", "20"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.KEY_REJECTED);
		assertThat(DataGoKrResponseReader
			.read(gatewayError("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", "22"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.QUOTA_EXCEEDED);
		assertThat(DataGoKrResponseReader.read(gatewayError("NODATA_ERROR", "03"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.NO_DATA);
	}

	@Test
	void read_shouldFallBackToReasonCodeWhenMessageIsUnknown() {
		assertThat(DataGoKrResponseReader.read(gatewayError("SOMETHING", "30"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.KEY_REJECTED);
		assertThat(DataGoKrResponseReader.read(gatewayError("SOMETHING", "22"), TRADE).result())
			.isEqualTo(DataGoKrResultCode.QUOTA_EXCEEDED);
	}

	@Test
	void read_shouldClassifyAbnormalResultCodeByMessage() {
		String body = "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}}}";

		assertThat(DataGoKrResponseReader.read(body, TRADE).result()).isEqualTo(DataGoKrResultCode.KEY_REJECTED);
	}

	@Test
	void read_shouldTreatNonJsonBodyAsUpstream() {
		assertThat(DataGoKrResponseReader.read("<html>Bad Gateway</html>", TRADE).result())
			.isEqualTo(DataGoKrResultCode.UPSTREAM);
		assertThat(DataGoKrResponseReader.read("", TRADE).result()).isEqualTo(DataGoKrResultCode.UPSTREAM);
		assertThat(DataGoKrResponseReader.read(null, TRADE).result()).isEqualTo(DataGoKrResultCode.UPSTREAM);
		assertThat(DataGoKrResponseReader.read("[1,2]", TRADE).result()).isEqualTo(DataGoKrResultCode.UPSTREAM);
	}

	@Test
	void read_shouldClassifyXmlKeyErrorBodyEvenThoughNotJson() {
		String xml = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
			+ "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";

		assertThat(DataGoKrResponseReader.read(xml, TRADE).result()).isEqualTo(DataGoKrResultCode.KEY_REJECTED);
	}

	@Test
	void text_shouldReadNumbersAndStringsAsTrimmedTextAndBlankAsNull() throws Exception {
		JsonNode row = new ObjectMapper().readTree("{\"a\":652,\"b\":\"0652\",\"c\":\" \",\"d\":null,\"e\":\" x \","
			+ "\"f\":104.43}");

		assertThat(DataGoKrResponseReader.text(row, "a")).isEqualTo("652");
		assertThat(DataGoKrResponseReader.text(row, "b")).isEqualTo("0652");
		assertThat(DataGoKrResponseReader.text(row, "c")).isNull();
		assertThat(DataGoKrResponseReader.text(row, "d")).isNull();
		assertThat(DataGoKrResponseReader.text(row, "missing")).isNull();
		assertThat(DataGoKrResponseReader.text(row, "e")).isEqualTo("x");
		assertThat(DataGoKrResponseReader.text(row, "f")).isEqualTo("104.43");
	}
}
