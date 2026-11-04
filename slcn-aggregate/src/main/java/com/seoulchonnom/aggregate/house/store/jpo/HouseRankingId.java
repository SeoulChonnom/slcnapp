package com.seoulchonnom.aggregate.house.store.jpo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HouseRankingId implements Serializable {
	private String rankingType;
	private String areaBucket;
	private int rankNo;
}
