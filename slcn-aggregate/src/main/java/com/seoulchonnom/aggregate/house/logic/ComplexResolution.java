package com.seoulchonnom.aggregate.house.logic;

import java.util.Map;

/**
 * 한 수집 단위의 단지 확정 결과.
 *
 * @param complexIdByAptSeq 단위에 나온 모든 aptSeq(대체 키 포함)의 단지 ID
 * @param created 새로 만든 단지 수
 * @param refreshed 표시 정보가 바뀌어 갱신한 단지 수
 */
public record ComplexResolution(Map<String, String> complexIdByAptSeq, int created, int refreshed) {
}
