package com.seoulchonnom.aggregate.house.collect;

/**
 * 건축물대장 조회 포트. 구현체 안에서만 외부 응답 형식을 안다.
 */
public interface BuildingRegisterGateway {
	/**
	 * 서비스 키가 있어 실제로 호출할 수 있는지. 야간 Flow가 시작할 때 먼저 본다.
	 */
	default boolean available() {
		return true;
	}

	/**
	 * 총괄표제부를 먼저 보고, 세대수·용적률·건폐율·사용승인일 중 빈 필드가 있으면 표제부로 그 필드만 채운다.
	 * 호출마다 예산을 먼저 쓰고, 예산이 없으면 호출하지 않는다.
	 *
	 * @param tracker 이번 실행의 총괄표제부 연속 실패 기록. 실행마다 새로 만들어 넘긴다.
	 */
	RegisterLookupResult find(String lawdCd, String umdCd, String bonbun, String bubun, CallBudget budget,
		RecapFailureTracker tracker);
}
