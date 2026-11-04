package com.seoulchonnom.aggregate.house.collect;

import java.time.YearMonth;

/**
 * 실거래 수집 포트. 구현체 안에서만 외부 응답 형식을 안다.
 */
public interface TradeGateway {
	/**
	 * 서비스 키가 있어 실제로 호출할 수 있는지. 야간 Flow가 시작할 때 먼저 본다.
	 */
	default boolean available() {
		return true;
	}

	/**
	 * 모든 페이지를 받는다. 페이지 하나를 부를 때마다 예산을 먼저 쓰고, 예산이 없으면 호출하지 않는다.
	 * 실패는 예외가 아니라 결과의 outcome으로 돌려준다.
	 */
	TradeFetchResult fetchAll(String lawdCd, YearMonth dealYm, CallBudget budget);
}
