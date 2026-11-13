package com.seoulchonnom.aggregate.flow.house;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 단계가 진행하면서 쌓는 호출 수와 건수. 단계가 예외로 끝나도 그때까지의 값이 기록에 남도록
 * 반환값이 아니라 이 객체에 쌓는다.
 */
public class StepProgress {
	private int apiCalls;
	private final Map<String, Object> detail = new LinkedHashMap<>();

	public int apiCalls() {
		return apiCalls;
	}

	public void apiCalls(int apiCalls) {
		this.apiCalls = apiCalls;
	}

	public Map<String, Object> detail() {
		return detail;
	}

	public void put(String key, Object value) {
		detail.put(key, value);
	}

	public void add(String key, int delta) {
		detail.merge(key, delta, (old, added) -> (Integer)old + (Integer)added);
	}
}
