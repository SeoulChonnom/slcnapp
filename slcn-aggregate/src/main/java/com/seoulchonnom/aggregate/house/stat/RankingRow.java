package com.seoulchonnom.aggregate.house.stat;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;

/**
 * house_ranking에 넣을 한 행. complexId·value는 구별 대장의 "거래 없음" 행에서 null이다.
 */
public record RankingRow(RankingType type, AreaBucket area, int rankNo, String lawdCd, String complexId,
						 BigDecimal value, Long changeManwon, int sampleCount, RankingBadge badge,
						 LocalDate periodFrom, LocalDate periodTo) {
}
