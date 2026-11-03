package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;

public interface HouseAdminFacade {
	/**
	 * 좌표 또는 건축물대장 보강이 실패한 단지. 최근 12개월 거래 건수 내림차순.
	 *
	 * @param page 1부터. 없으면 1
	 * @param size 1~100, 없으면 20
	 */
	ResponseEntity<IncompleteComplexListRdo> getIncompleteComplexes(Integer page, Integer size);

	/**
	 * 주소·지번 보정. 실제 재조회는 다음 야간 배치다.
	 */
	ResponseEntity<IncompleteComplexRdo> overrideAddress(String complexId, AddressOverrideUdo request);
}
