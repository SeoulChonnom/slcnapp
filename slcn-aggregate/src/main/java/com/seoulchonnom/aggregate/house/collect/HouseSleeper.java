package com.seoulchonnom.aggregate.house.collect;

import java.time.Duration;

/**
 * 재시도 대기. 테스트에서 0초로 바꿀 수 있게 인터페이스로 둔다.
 */
@FunctionalInterface
public interface HouseSleeper {
	void sleep(Duration duration) throws InterruptedException;
}
