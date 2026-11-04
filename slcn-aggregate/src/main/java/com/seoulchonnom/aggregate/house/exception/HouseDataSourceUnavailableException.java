package com.seoulchonnom.aggregate.house.exception;

/**
 * 서비스 키가 없어 외부 데이터 소스를 쓸 수 없을 때 Disabled 게이트웨이가 던진다.
 * 야간 배치 내부용이라 응답으로 나가지 않는다.
 */
public class HouseDataSourceUnavailableException extends RuntimeException {
	public HouseDataSourceUnavailableException(String source) {
		super(source + " 서비스 키가 설정되지 않았습니다.");
	}
}
