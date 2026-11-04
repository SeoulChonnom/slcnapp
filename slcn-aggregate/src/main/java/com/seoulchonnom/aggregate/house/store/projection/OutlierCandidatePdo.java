package com.seoulchonnom.aggregate.house.store.projection;

import java.time.LocalDate;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

/**
 * 이상치 판정에 필요한 거래 컬럼만. 약 20만 행을 한 번에 읽으므로 엔티티 전체를 올리지 않는다.
 */
public record OutlierCandidatePdo(String id, String complexId, AreaBucket areaBucket, LocalDate dealDate,
								  long amountManwon) {
}
