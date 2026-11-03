package com.seoulchonnom.aggregate.house.store.projection;

import com.seoulchonnom.spec.house.entity.HouseComplex;

/**
 * 관리자 보강 실패 목록의 한 행. 단지와 최근 12개월 거래 건수.
 */
public record IncompleteComplexPdo(HouseComplex complex, long trades12m) {
}
