package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AreaInfoRdo {
	/** 해당 구간 최근 12개월 거래의 전용면적 평균. 통계가 없으면 null */
	private BigDecimal avgExclusiveArea;
	private AreaBucket bucket;
}
