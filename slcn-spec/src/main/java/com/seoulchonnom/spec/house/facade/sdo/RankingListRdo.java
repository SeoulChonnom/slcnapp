package com.seoulchonnom.spec.house.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RankingListRdo {
	private RankingType type;
	private AreaBucket area;
	/** 랭킹 행이 없으면(통계가 한 번도 안 돌았음) null */
	private PeriodRdo period;
	private List<RankingItemRdo> items;
}
