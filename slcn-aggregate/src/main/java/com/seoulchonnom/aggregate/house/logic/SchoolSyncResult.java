package com.seoulchonnom.aggregate.house.logic;

/**
 * 학교 교체·최근접 재계산 결과 건수.
 *
 * @param schools 교체 후 house_school 행 수
 * @param deleted 이번 목록에 없어 지운 학교 수(최근접만 다시 계산했으면 0)
 * @param complexes 계산 대상(좌표 있는) 단지 수
 * @param nearestRows house_complex_school에 넣은 행 수
 */
public record SchoolSyncResult(int schools, int deleted, int complexes, int nearestRows) {
}
