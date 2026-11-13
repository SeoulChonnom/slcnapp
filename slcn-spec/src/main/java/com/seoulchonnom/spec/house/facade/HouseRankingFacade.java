package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingType;
import com.seoulchonnom.spec.house.facade.sdo.RankingListRdo;

public interface HouseRankingFacade {
	/**
	 * 미리 계산해 둔 랭킹을 순위 순으로. 랭킹이 없으면 items는 빈 배열, period는 null이다.
	 *
	 * @param type LEADER, RISE, FALL, VOLUME
	 * @param area B59 또는 B84 (OTHER는 400)
	 */
	ResponseEntity<RankingListRdo> getRankings(RankingType type, AreaBucket area);
}
