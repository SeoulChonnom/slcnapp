package com.seoulchonnom.aggregate.house.collect;

/**
 * 학교 위치 수집 포트. 구현체 안에서만 외부 응답 형식을 안다.
 */
public interface SchoolGateway {
	/**
	 * 서비스 키가 있어 실제로 호출할 수 있는지. 야간 Flow가 시작할 때 먼저 본다.
	 */
	default boolean available() {
		return true;
	}

	/**
	 * 서울 학교 전체를 받는다. 페이지 하나를 부를 때마다 예산을 먼저 쓰고, 예산이 없으면 호출하지 않는다.
	 * 일부 페이지라도 실패하면 OK가 아니다(일부만으로 기존 목록을 교체하지 않으려는 것이다).
	 */
	SchoolFetchResult fetchAll(CallBudget budget);
}
