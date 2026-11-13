package com.seoulchonnom.aggregate.external.datagokr;

/**
 * 2026-10-21 실제 호출 응답에서 가져온 픽스처.
 */
final class TradeFixtures {
	private TradeFixtures() {
	}

	static final String ROW_GAEPO = """
		{"aptDong":" ","aptNm":"개포우성3차","aptSeq":"11680-283","bonbun":"0652","bubun":"0000","buildYear":1984,"buyerGbn":"개인","cdealDay":" ","cdealType":" ","dealAmount":"295,000","dealDay":22,"dealMonth":9,"dealYear":2026,"dealingGbn":"중개거래","estateAgentSggNm":"서울 강남구","excluUseAr":104.43,"floor":7,"jibun":652,"landCd":1,"landLeaseholdGbn":"N","rgstDate":" ","roadNm":"개포로","roadNmBonbun":"00307","roadNmBubun":"00000","roadNmCd":3122001,"roadNmSeq":"01","roadNmSggCd":11680,"roadNmbCd":0,"sggCd":11680,"slerGbn":"개인","umdCd":10300,"umdNm":"개포동"}""";

	static final String ROW_DOGOK = """
		{"aptDong":" ","aptNm":"도곡3차아이파크","aptSeq":"11680-3741","bonbun":"0970","bubun":"0000","buildYear":2007,"buyerGbn":"개인","cdealDay":" ","cdealType":" ","dealAmount":"310,000","dealDay":14,"dealMonth":9,"dealYear":2026,"dealingGbn":"직거래","estateAgentSggNm":" ","excluUseAr":181.381,"floor":3,"jibun":970,"landCd":1,"landLeaseholdGbn":"N","rgstDate":" ","roadNm":"도곡로14길","roadNmBonbun":"00021","roadNmBubun":"00000","roadNmCd":4166215,"roadNmSeq":"01","roadNmSggCd":11680,"roadNmbCd":0,"sggCd":11680,"slerGbn":"개인","umdCd":11800,"umdNm":"도곡동"}""";

	static final String ROW_WOLSEONG = """
		{"aptDong":" ","aptNm":"월성빌라트","aptSeq":"11680-680","bonbun":"0091","bubun":"0016","buildYear":1994,"buyerGbn":"개인","cdealDay":" ","cdealType":" ","dealAmount":"172,000","dealDay":19,"dealMonth":9,"dealYear":2026,"dealingGbn":"중개거래","estateAgentSggNm":"서울 강남구","excluUseAr":81.79,"floor":1,"jibun":"91-16","landCd":1,"landLeaseholdGbn":"N","rgstDate":" ","roadNm":"영동대로114길","roadNmBonbun":"00023","roadNmBubun":"00000","roadNmCd":4166678,"roadNmSeq":"01","roadNmSggCd":11680,"roadNmbCd":0,"sggCd":11680,"slerGbn":"개인","umdCd":10500,"umdNm":"삼성동"}""";

	/** 해제 행: cdealType "O", cdealDay "26.08.28", 숫자로 온 bonbun(652)과 aptDong(101) */
	static final String ROW_CANCELLED = """
		{"aptDong":101,"aptNm":"개포우성3차","aptSeq":"11680-283","bonbun":652,"bubun":0,"buildYear":1984,"cdealDay":"26.08.28","cdealType":"O","dealAmount":"290,000","dealDay":10,"dealMonth":8,"dealYear":2026,"dealingGbn":" ","excluUseAr":"104.43","floor":"-1","roadNm":"개포로","roadNmBonbun":"00307","roadNmBubun":"00000","sggCd":11680,"umdCd":10300,"umdNm":"개포동"}""";

	static String page(int totalCount, int pageNo, String... rows) {
		return "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},\"body\":{\"items\":{\"item\":["
			+ String.join(",", rows) + "]},\"numOfRows\":" + rows.length + ",\"pageNo\":" + pageNo
			+ ",\"totalCount\":" + totalCount + "}}}";
	}

	static final String THREE_ROWS = page(116, 1, ROW_GAEPO, ROW_DOGOK, ROW_WOLSEONG);

	static final String SINGLE_ROW_AS_OBJECT = "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},"
		+ "\"body\":{\"items\":{\"item\":" + ROW_GAEPO + "},\"numOfRows\":1000,\"pageNo\":1,\"totalCount\":1}}}";

	static final String EMPTY_ITEMS = "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},"
		+ "\"body\":{\"items\":\"\",\"numOfRows\":1000,\"pageNo\":1,\"totalCount\":0}}}";

	static final String GATEWAY_TIMEOUT = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"SERVICETIMEOUT_ERROR\","
		+ "\"returnAuthMsg\":\"서비스 연결실패 에러\",\"returnReasonCode\":\"05\"}}}";

	static String gatewayError(String errMsg, String reasonCode) {
		return "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"" + errMsg + "\",\"returnAuthMsg\":\"x\","
			+ "\"returnReasonCode\":\"" + reasonCode + "\"}}}";
	}
}
