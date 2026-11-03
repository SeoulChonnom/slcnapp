package com.seoulchonnom.aggregate.house.store;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.stat.HouseStatWindows;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexAreaStatId;
import com.seoulchonnom.aggregate.house.store.projection.AreaStatPdo;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexAreaStatRepository;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

import lombok.RequiredArgsConstructor;

/**
 * 파생 통계 테이블. 쓰기는 통째로 다시 만드는 것 하나뿐이고, 읽기는 Rdo 조립용 Pdo로 돌려준다.
 */
@Repository
@RequiredArgsConstructor
public class HouseStatStore {
	private final HouseComplexAreaStatRepository houseComplexAreaStatRepository;

	/**
	 * 통계를 지우고 거래에서 다시 만든다. 호출하는 쪽의 트랜잭션 안에서 불러야 중간 상태가 보이지 않는다.
	 *
	 * @return 만든 행 수
	 */
	public int rebuildAreaStats(HouseStatWindows windows) {
		houseComplexAreaStatRepository.deleteAllStats();
		return houseComplexAreaStatRepository.insertStats(windows.today(), windows.w3mStart(), windows.w6mStart(),
			windows.w12mStart(), windows.w3m1yStart(), windows.w3m1yEnd(), windows.w3m3yStart(), windows.w3m3yEnd());
	}

	public Optional<AreaStatPdo> findAreaStat(String complexId, AreaBucket areaBucket) {
		return houseComplexAreaStatRepository.findById(new HouseComplexAreaStatId(complexId, areaBucket.name()))
			.map(stat -> new AreaStatPdo(stat.getRecentPriceManwon(), stat.getRecentWindowMonths(),
				stat.getRecentSample()));
	}
}
