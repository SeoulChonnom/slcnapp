package com.seoulchonnom.aggregate.house.store.projection;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

/**
 * 비교표가 읽는 (단지, 면적 구간) 통계 한 행. 건수는 not null, 평균·변동은 건수가 모자라면 null이다.
 */
public record CompareStatPdo(String complexId, AreaBucket areaBucket, BigDecimal avgArea12m, Long recentPriceManwon,
							 Integer recentWindowMonths, int recentSample, Long avg3m, int cnt3m, Long avg3m1yAgo,
							 int cnt3m1yAgo, BigDecimal change1yPct, Long change1yManwon, Long avg3m3yAgo,
							 int cnt3m3yAgo, BigDecimal change3yPct, Long change3yManwon) {
}
