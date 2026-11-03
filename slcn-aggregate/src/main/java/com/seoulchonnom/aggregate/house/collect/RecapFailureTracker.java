package com.seoulchonnom.aggregate.house.collect;

/**
 * 한 번의 야간 실행 동안 총괄표제부가 연달아 UPSTREAM으로 실패한 횟수. 게이트웨이는 싱글턴이라 실행별 상태는 이 객체에 둔다.
 * 연속 3번 실패하면 남은 단지는 총괄표제부를 건너뛴다. 타임아웃이 단지마다 이어지면 배치가 몇 시간 늘어나기 때문이다.
 * 단일 스레드(스케줄러 스레드)에서만 쓴다.
 */
public class RecapFailureTracker {
	static final int SKIP_AFTER = 3;

	private int streak;

	public boolean shouldSkip() {
		return streak >= SKIP_AFTER;
	}

	public void recordUpstreamFailure() {
		streak++;
	}

	/** 총괄표제부가 응답을 줬으면(결과가 없어도) 연속 실패를 끊는다. */
	public void recordAnswered() {
		streak = 0;
	}

	public int streak() {
		return streak;
	}
}
