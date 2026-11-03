package com.seoulchonnom.aggregate.house.collect;

/**
 * API 하나의 하루 호출 예산. 야간 실행 1회마다 API별로 하나씩 만든다.
 * 같은 날 두 번 돌아도 한도를 넘지 않도록 "오늘 이미 쓴 호출 수"를 빼고 시작한다.
 * 단일 스레드(스케줄러 스레드)에서만 쓴다.
 */
public class CallBudget {
	private final String name;
	private final int limit;
	private final long alreadyUsed;
	private int consumed;

	public CallBudget(String name, int limit, long alreadyUsedToday) {
		this.name = name;
		this.limit = limit;
		this.alreadyUsed = Math.max(0, alreadyUsedToday);
	}

	/**
	 * 호출 한 번분을 쓴다. 남은 예산이 없으면 쓰지 않고 false를 돌려주며, 호출하는 쪽은 호출하지 않아야 한다.
	 */
	public boolean consume() {
		if (remaining() <= 0) {
			return false;
		}
		consumed++;
		return true;
	}

	public long remaining() {
		return Math.max(0, limit - alreadyUsed - consumed);
	}

	/**
	 * 이번 실행에서 쓴 호출 수. house_batch_run.api_calls에 기록한다.
	 */
	public int used() {
		return consumed;
	}

	public String name() {
		return name;
	}
}
