package com.seoulchonnom.aggregate.house.logic;

/**
 * 수집 단위 하나를 반영한 건수. HouseBatchRun.detail에 단위마다 누적한다.
 *
 * @param fetched 응답에서 받은 행 수(버린 행 제외)
 * @param inserted 새 거래
 * @param updated 해제·거래 유형·removed 복구로 갱신된 거래
 * @param unchanged 값이 같아 쓰지 않은 거래
 * @param cancelled 이번 응답에서 해제 상태인 거래
 * @param removed 이번에 원천 삭제로 표시된 거래
 * @param skippedRows 필수값을 해석하지 못해 버린 행
 * @param emptyResponse 모든 페이지를 받았는데 거래가 0건이었는지(원천 삭제 판정을 하지 않는다)
 */
public record TradeApplyResult(int fetched, int inserted, int updated, int unchanged, int cancelled, int removed,
							   int skippedRows, int complexCreated, int complexRefreshed, boolean emptyResponse) {
}
