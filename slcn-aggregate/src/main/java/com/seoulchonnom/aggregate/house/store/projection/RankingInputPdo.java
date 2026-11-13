package com.seoulchonnom.aggregate.house.store.projection;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

/**
 * 랭킹 계산 입력 한 행(통계 + 단지 이름·구). 통계 값은 null일 수 있다.
 */
public record RankingInputPdo(String complexId, AreaBucket area, String lawdCd, String aptName, int cnt12m,
							  Long avg12m, int cnt3m, int cnt3m1yAgo, int volume3m, BigDecimal change1yPct,
							  Long change1yManwon) {
}
