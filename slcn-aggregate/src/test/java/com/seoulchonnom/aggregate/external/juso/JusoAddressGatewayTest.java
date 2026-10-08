package com.seoulchonnom.aggregate.external.juso;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.seoulchonnom.aggregate.inspection.exception.AddressCoordinateNotFoundException;
import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;
import com.seoulchonnom.aggregate.inspection.geo.AddressSearchResult;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.DisabledAddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class JusoAddressGatewayTest {
	private static final String BASE = "https://juso.test";
	private static final CoordKey KEY = new CoordKey("1120011500", "112003115001", "0", "10", "0");

	private MockRestServiceServer server;
	private JusoAddressGateway gateway;
	private JusoAddressGateway searchOnly;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		server = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();
		gateway = new JusoAddressGateway(restClient, "sk", "ck");
		searchOnly = new JusoAddressGateway(restClient, "sk", "");
	}

	private static String error(String code, String message) {
		return "{\"results\":{\"common\":{\"errorMessage\":\"" + message + "\",\"errorCode\":\"" + code
			+ "\",\"totalCount\":\"0\",\"currentPage\":\"1\",\"countPerPage\":\"10\"},\"juso\":null}}";
	}

	private void respond(String body) {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void search_shouldParseCandidatesAndSendEncodedParams() {
		server.expect(requestTo(BASE + "/addrlink/addrLinkApi.do?confmKey=sk&currentPage=2&countPerPage=10"
			+ "&keyword=%EC%84%B1%EC%88%98%20%ED%8A%B8%EB%A6%AC%EB%A7%88%EC%A0%9C&resultType=json"))
			.andRespond(withSuccess("""
				{"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"12","currentPage":"2","countPerPage":"10"},
				"juso":[{"roadAddr":"서울특별시 성동구 용답토방길 135","jibunAddr":"서울특별시 성동구 성수동1가 685-1","bdNm":"트리마제",
				"bdMgtSn":"1120011500106850001000001","zipNo":"04773","admCd":"1120011500","rnMgtSn":"112003115001","udrtYn":"0",
				"buldMnnm":"135","buldSlno":"0"}]}}""", MediaType.APPLICATION_JSON));

		AddressSearchResult result = gateway.search("성수 트리마제", 2, 10);

		assertThat(result.totalCount()).isEqualTo(12);
		assertThat(result.candidates()).hasSize(1);
		assertThat(result.candidates().get(0).roadAddress()).isEqualTo("서울특별시 성동구 용답토방길 135");
		assertThat(result.candidates().get(0).buildingName()).isEqualTo("트리마제");
		assertThat(result.candidates().get(0).coordKey())
			.isEqualTo(new CoordKey("1120011500", "112003115001", "0", "135", "0"));
		server.verify();
	}

	@Test
	void search_shouldReturnEmptyWhenJusoIsNull() {
		respond("{\"results\":{\"common\":{\"errorCode\":\"0\",\"errorMessage\":\"정상\",\"totalCount\":\"0\"},\"juso\":null}}");

		AddressSearchResult result = gateway.search("없는주소", 1, 10);

		assertThat(result.totalCount()).isZero();
		assertThat(result.candidates()).isEmpty();
	}

	@Test
	void findEntrance_shouldParseStringCoordinates() {
		server.expect(requestTo(BASE + "/addrlink/addrCoordApi.do?confmKey=ck&admCd=1120011500&rnMgtSn=112003115001"
			+ "&udrtYn=0&buldMnnm=10&buldSlno=0&resultType=json"))
			.andRespond(withSuccess("""
				{"results":{"common":{"errorCode":"0","errorMessage":"정상","totalCount":"1"},
				"juso":[{"admCd":"1120011500","entX":"960774.604","entY":"1949564.899"}]}}""",
				MediaType.APPLICATION_JSON));

		assertThat(gateway.findEntrance(KEY)).isEqualTo(new UtmkPoint(960774.604, 1949564.899));
		server.verify();
	}

	@Test
	void findEntrance_shouldFailAsBadRequestWhenNoCoordinates() {
		respond("{\"results\":{\"common\":{\"errorCode\":\"0\",\"totalCount\":\"0\"},\"juso\":null}}");

		assertThatThrownBy(() -> gateway.findEntrance(KEY)).isInstanceOfSatisfying(
			AddressCoordinateNotFoundException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_COORDINATE_NOT_FOUND));
	}

	@Test
	void findEntrance_shouldStayUpstreamFailureWhenCoordinatesAreMalformed() {
		respond("{\"results\":{\"common\":{\"errorCode\":\"0\",\"totalCount\":\"1\"},\"juso\":[{\"entX\":\"abc\",\"entY\":\"\"}]}}");

		assertThatThrownBy(() -> gateway.findEntrance(KEY)).isInstanceOfSatisfying(
			AddressLookupUnavailableException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_FAILED));
	}

	@Test
	void keywordErrors_shouldBecomeInvalidKeywordWithUpstreamMessage() {
		for (String code : new String[] {"E0005", "E0006", "E0008", "E0009", "E0012", "E0013"}) {
			server.reset();
			respond(error(code, "고쳐 주세요 " + code));
			assertThatThrownBy(() -> gateway.search("x", 1, 10)).isInstanceOfSatisfying(
				InvalidAddressKeywordException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADDRESS_KEYWORD);
					assertThat(e.getMessage()).isEqualTo("고쳐 주세요 " + code);
				});
		}
	}

	@Test
	void keyErrors_shouldBecomeUnavailable503() {
		for (String code : new String[] {"E0001", "E0014"}) {
			server.reset();
			respond(error(code, "키 오류"));
			assertThatThrownBy(() -> gateway.search("성수동", 1, 10)).isInstanceOfSatisfying(
				AddressLookupUnavailableException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_UNAVAILABLE));
		}
	}

	@Test
	void systemUnknownAndBrokenResponses_shouldBecomeBadGateway502() {
		String[] bodies = {error("-999", "시스템 에러"), error("E9999", "?"), "<html>oops</html>", "{}", "",
			"{\"results\":{\"common\":{\"errorCode\":\"0\",\"totalCount\":\"abc\"},\"juso\":null}}"};
		for (String body : bodies) {
			server.reset();
			respond(body);
			assertThatThrownBy(() -> gateway.search("성수동", 1, 10)).isInstanceOfSatisfying(
				AddressLookupUnavailableException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_FAILED));
		}
	}

	@Test
	void httpErrorAndIoFailure_shouldBecomeBadGateway502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		assertThatThrownBy(() -> gateway.search("성수동", 1, 10)).isInstanceOfSatisfying(
			AddressLookupUnavailableException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_FAILED));

		server.reset();
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
			.andRespond(request -> {
				throw new java.io.IOException("timeout confmKey=sk");
			});
		assertThatThrownBy(() -> gateway.search("성수동", 1, 10)).isInstanceOfSatisfying(
			AddressLookupUnavailableException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_FAILED);
				assertThat(e.getCause()).isNull();
				assertThat(e.getMessage()).doesNotContain("sk");
			});
	}

	@Test
	void blankKey_shouldDisableOnlyThatOperation() {
		assertThatThrownBy(() -> searchOnly.findEntrance(KEY)).isInstanceOfSatisfying(
			AddressLookupUnavailableException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_LOOKUP_UNAVAILABLE));

		respond("{\"results\":{\"common\":{\"errorCode\":\"0\",\"totalCount\":\"0\"},\"juso\":null}}");
		assertThat(searchOnly.search("성수동", 1, 10).candidates()).isEmpty();
	}

	@Test
	void disabledGateway_shouldAlwaysThrow503() {
		DisabledAddressGateway disabled = new DisabledAddressGateway();
		assertThatThrownBy(() -> disabled.search("a", 1, 10)).isInstanceOf(AddressLookupUnavailableException.class);
		assertThatThrownBy(() -> disabled.findEntrance(KEY)).isInstanceOf(AddressLookupUnavailableException.class);
	}
}
