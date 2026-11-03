package com.seoulchonnom.aggregate.house.collect;

import java.util.List;

/**
 * 정비사업 수집 포트. 데이터셋이 미확정이라 구현체가 없고 항상 DisabledRedevelopmentGateway가 쓰인다.
 * 데이터셋이 정해지면 구현체 하나만 추가한다.
 */
public interface RedevelopmentGateway {
	boolean available();

	List<RedevelopmentRecord> fetchAll();
}
