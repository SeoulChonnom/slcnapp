package com.seoulchonnom.aggregate.house.collect;

import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

/**
 * 학교 표준데이터 한 행을 우리 쪽 형태로 옮긴 것. 게이트웨이 밖으로는 이것만 나간다.
 */
public record SchoolRecord(String schoolCode, String name, SchoolLevel level, double latitude, double longitude) {
}
