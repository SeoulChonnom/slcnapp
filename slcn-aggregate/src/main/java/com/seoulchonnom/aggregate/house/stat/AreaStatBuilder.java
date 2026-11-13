package com.seoulchonnom.aggregate.house.stat;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.house.store.HouseStatStore;

import lombok.RequiredArgsConstructor;

/**
 * 단지·평형 통계를 통째로 다시 만든다. 계산은 HouseStatStore의 INSERT…SELECT 한 문장이 하고,
 * 이 클래스는 통계 창 경계(HouseStatWindows)를 넘기는 자리다. 호출하는 쪽(HouseStatLogic)의 트랜잭션 안에서 돈다.
 *
 * 파생 규칙은 쿼리에 들어 있다: avg는 만원 정수로 반올림, 변동률은 반올림된 평균으로 계산,
 * 최근 실거래가는 3개월 → 6개월 대체, volume_3m은 이상치를 포함한다.
 */
@Component
@RequiredArgsConstructor
public class AreaStatBuilder {
	private final HouseStatStore houseStatStore;

	/**
	 * @return 만든 통계 행 수
	 */
	public int rebuild(HouseStatWindows windows) {
		return houseStatStore.rebuildAreaStats(windows);
	}
}
