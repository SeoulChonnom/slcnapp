package com.seoulchonnom.rest.inspection;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyLocationRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

/**
 * 매물 위치의 요청/응답 JSON 형태를 고정한다. 요청에는 좌표가 없고, 응답에는 원본 좌표(entX/entY)가 없다.
 */
class ViewedPropertyLocationJsonContractTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final String LOCATION_JSON = "\"location\":{\"bdMgtSn\":\"1171010200\","
		+ "\"roadAddress\":\"서울 송파구 올림픽로 1\",\"coordKey\":{\"admCd\":\"1171010200\","
		+ "\"rnMgtSn\":\"117103123001\",\"udrtYn\":\"0\",\"buldMnnm\":\"1\",\"buldSlno\":\"0\"}}";

	@Test
	void cdoAndUdo_shouldReadLocationWithCoordKey() throws Exception {
		ViewedPropertyCdo cdo = objectMapper.readValue("{\"complexName\":\"트리마제\"," + LOCATION_JSON + "}",
			ViewedPropertyCdo.class);
		ViewedPropertyUdo udo = objectMapper.readValue("{\"complexName\":\"트리마제\"," + LOCATION_JSON + "}",
			ViewedPropertyUdo.class);

		for (var location : new com.seoulchonnom.spec.inspection.facade.sdo.PropertyLocationInputSdo[] {
			cdo.getLocation(), udo.getLocation()}) {
			assertThat(location.getBdMgtSn()).isEqualTo("1171010200");
			assertThat(location.getRoadAddress()).isEqualTo("서울 송파구 올림픽로 1");
			assertThat(location.getCoordKey().getRnMgtSn()).isEqualTo("117103123001");
			assertThat(location.getCoordKey().getBuldSlno()).isEqualTo("0");
		}
	}

	@Test
	void cdoAndUdo_shouldTreatMissingOrNullLocationAsNull() throws Exception {
		assertThat(objectMapper.readValue("{\"complexName\":\"a\"}", ViewedPropertyUdo.class).getLocation()).isNull();
		assertThat(objectMapper.readValue("{\"complexName\":\"a\",\"location\":null}", ViewedPropertyCdo.class)
			.getLocation()).isNull();
	}

	@Test
	void locationRdo_shouldSerializeOnlyIdentityAddressAndWgs84() throws Exception {
		JsonNode json = objectMapper.valueToTree(new PropertyLocationRdo("1171010200", "서울 송파구", 37.5, 127.1));

		assertThat(json.fieldNames()).toIterable()
			.containsExactlyInAnyOrder("bdMgtSn", "roadAddress", "latitude", "longitude");
	}
}
