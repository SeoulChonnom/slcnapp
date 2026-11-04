package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.facade.sdo.HouseMetaRdo;

public interface HouseMetaFacade {
	/**
	 * 데이터 기준일과 통계 계산일. 화면 상단 "기준일" 표시에 쓴다.
	 */
	ResponseEntity<HouseMetaRdo> getMeta();
}
