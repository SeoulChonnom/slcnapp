package com.seoulchonnom.aggregate.external.datagokr;

/**
 * 2026-11-03 실제 호출 응답을 줄여 옮긴 픽스처.
 */
final class RegisterFixtures {
	private RegisterFixtures() {
	}

	/** 개포동 652 우성아파트 총괄표제부. 용적률·건폐율이 0이고 사용승인일이 빈 값이며 세대수만 있다. */
	static final String RECAP_ITEM_OLD = """
		{"rnum":1,"platPlc":"서울특별시 강남구 개포동 652번지","sigunguCd":"11680","bjdongCd":"10300","platGbCd":"0","bun":"0652","ji":"0000","mgmBldrgstPk":10241156,"regstrGbCd":"2","regstrGbCdNm":"집합","regstrKindCd":"1","regstrKindCdNm":"총괄표제부","bldNm":"우성아파트","platArea":0,"archArea":4484.94,"bcRat":0,"totArea":61999.08,"vlRatEstmTotArea":57395.16,"vlRat":0,"mainPurpsCd":"02000","mainPurpsCdNm":"공동주택","hhldCnt":405,"fmlyCnt":0,"mainBldCnt":6,"useAprDay":" ","crtnDay":"20220813"}""";

	/** 값이 모두 채워진 총괄표제부 */
	static final String RECAP_ITEM_FULL = """
		{"regstrKindCdNm":"총괄표제부","bldNm":"래미안","bcRat":19.37,"totArea":150000.5,"vlRat":249.99,"mainPurpsCdNm":"공동주택","hhldCnt":1200,"useAprDay":"20080530"}""";

	/** 용적률은 있고 사용승인일이 빈 값인 총괄표제부 */
	static final String RECAP_ITEM_FAR_ONLY = """
		{"regstrKindCdNm":"총괄표제부","bcRat":0,"vlRat":199.5,"mainPurpsCdNm":"공동주택","hhldCnt":300,"useAprDay":" "}""";

	static final String TITLE_SHOP = """
		{"rnum":1,"regstrKindCdNm":"표제부","bldNm":"우성아파트상가동","dongNm":"상가","mainPurpsCdNm":"판매시설","platArea":0,"archArea":625.12,"bcRat":0,"totArea":99999.46,"vlRat":300,"hhldCnt":0,"useAprDay":"19841231"}""";

	static final String TITLE_APT_LARGE = """
		{"rnum":2,"regstrKindCdNm":"표제부","bldNm":"우성아파트","dongNm":"101동","mainPurpsCdNm":"공동주택","archArea":900.1,"bcRat":18.2,"totArea":30000.5,"vlRat":249.5,"hhldCnt":200,"useAprDay":"19840831"}""";

	static final String TITLE_APT_SMALL = """
		{"rnum":3,"regstrKindCdNm":"표제부","bldNm":"우성아파트","dongNm":"102동","mainPurpsCdNm":"공동주택","archArea":700.1,"bcRat":0,"totArea":20000.5,"vlRat":0,"hhldCnt":205,"useAprDay":"19850101"}""";

	static final String TITLE_APT_EMPTY_VALUES = """
		{"rnum":4,"regstrKindCdNm":"표제부","dongNm":"103동","mainPurpsCdNm":"공동주택","totArea":40000,"bcRat":0,"vlRat":0,"hhldCnt":100,"useAprDay":" "}""";

	static String envelope(int totalCount, String... items) {
		return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE\"},\"body\":{\"items\":"
			+ (items.length == 0 ? "\"\"" : "{\"item\":[" + String.join(",", items) + "]}")
			+ ",\"numOfRows\":100,\"pageNo\":1,\"totalCount\":" + totalCount + "}}}";
	}

	static final String GATEWAY_TIMEOUT = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{\"errMsg\":\"SERVICETIMEOUT_ERROR\","
		+ "\"returnAuthMsg\":\"서비스 연결실패 에러\",\"returnReasonCode\":\"05\"}}}";
}
