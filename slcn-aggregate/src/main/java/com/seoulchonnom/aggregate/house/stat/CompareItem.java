package com.seoulchonnom.aggregate.house.stat;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

/**
 * 비교 요청의 항목 하나(단지, 면적 구간).
 */
public record CompareItem(String complexId, AreaBucket area) {
}
