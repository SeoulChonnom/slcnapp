package com.seoulchonnom.aggregate.house.store.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.RankingBadge;

/**
 * 랭킹 조회 한 행. 단지·통계는 LEFT JOIN이라 "거래 없음" 행에서는 단지 관련 값이 모두 null이다.
 */
public record RankingRowPdo(int rankNo, String complexId, String aptName, String lawdCd, Double latitude,
							Double longitude, Integer buildYear, BigDecimal avgArea12m, Integer households,
							BigDecimal value, Long changeManwon, int sampleCount, RankingBadge badge,
							LocalDate periodFrom, LocalDate periodTo) {
}
