package com.seoulchonnom.aggregate.house.collect;

import java.util.List;

/**
 * 학교 목록을 모든 페이지까지 받은 결과.
 *
 * @param records outcome이 OK일 때만 전체이고, 그 밖에는 일부이므로 교체에 쓰지 않는다.
 * @param skippedRows 서울이 아니거나 운영 중이 아니거나 학교급·좌표를 해석하지 못해 버린 행 수
 */
public record SchoolFetchResult(List<SchoolRecord> records, Outcome outcome, int skippedRows) {

	public enum Outcome {
		/** 모든 페이지 성공 */
		OK,
		/** 호출 예산이 없어 시작하지 못했거나 도중에 멈춤 */
		BUDGET_EXHAUSTED,
		/** 일일 한도 초과 */
		QUOTA_EXCEEDED,
		/** 인증 키 거절 */
		KEY_REJECTED,
		/** 재시도 후에도 실패 */
		FAILED
	}

	public static SchoolFetchResult ok(List<SchoolRecord> records, int skippedRows) {
		return new SchoolFetchResult(records, Outcome.OK, skippedRows);
	}

	public static SchoolFetchResult stopped(Outcome outcome, int skippedRows) {
		return new SchoolFetchResult(List.of(), outcome, skippedRows);
	}
}
