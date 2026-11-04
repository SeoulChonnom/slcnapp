package com.seoulchonnom.aggregate.house.logic;

/**
 * 정비사업 CSV 통째 교체 결과.
 *
 * @param total          파일의 구역 수
 * @param geocodePending 교체 뒤 좌표를 기다리는 구역 수
 */
public record RedevelopmentReplaceResult(int total, int inserted, int updated, int unchanged, int deleted,
										 long geocodePending) {
}
