package com.seoulchonnom.aggregate.house.collect;

import java.util.List;

/**
 * 한 (구, 계약년월)을 모든 페이지까지 받은 결과.
 *
 * @param complete 모든 페이지를 성공적으로 받았는지. false면 records는 일부이므로 원천 삭제 판정에 쓰지 않는다.
 * @param skippedRows 필수값을 해석하지 못해 버린 행 수
 */
public record TradeFetchResult(List<TradeRecord> records, boolean complete, Outcome outcome, int skippedRows) {

	public enum Outcome {
		/** 모든 페이지 성공(빈 결과 포함) */
		OK,
		/** 호출 예산이 없어 시작하지 못했거나 도중에 멈춤. 단위 상태는 그대로 둔다. */
		BUDGET_EXHAUSTED,
		/** 일일 한도 초과. 단계를 멈추고 남은 단위는 다음 날 한다. */
		QUOTA_EXCEEDED,
		/** 인증 키 거절. 단계를 멈춘다. */
		KEY_REJECTED,
		/** 재시도 후에도 실패 */
		FAILED
	}

	public static TradeFetchResult ok(List<TradeRecord> records, int skippedRows) {
		return new TradeFetchResult(records, true, Outcome.OK, skippedRows);
	}

	public static TradeFetchResult stopped(Outcome outcome, List<TradeRecord> partial, int skippedRows) {
		return new TradeFetchResult(partial, false, outcome, skippedRows);
	}
}
