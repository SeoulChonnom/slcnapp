package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.ComplexGroupBy;
import com.seoulchonnom.spec.house.facade.sdo.ComplexCardRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexMapListRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryRdo;

/**
 * 단지 조회. 단지명 검색(/search)은 다음 PR에서 추가한다.
 */
public interface HouseComplexFacade {
	/**
	 * 지도 영역(swLat/swLng/neLat/neLng) 또는 lawdCd 중 하나로 단지를 찾는다. 최대 300건.
	 *
	 * @param area B59 또는 B84 (OTHER는 400)
	 * @param minPrice 만원, 포함. 없으면 열림
	 * @param maxPrice 만원, 포함. 없으면 열림
	 */
	ResponseEntity<ComplexMapListRdo> getComplexes(Double swLat, Double swLng, Double neLat, Double neLng,
		String lawdCd, AreaBucket area, Long minPrice, Long maxPrice);

	/**
	 * 구·동 묶음 후보 수. DISTRICT는 영역을 받지 않고, DONG은 영역이 필수다.
	 */
	ResponseEntity<ComplexSummaryRdo> getSummary(ComplexGroupBy groupBy, AreaBucket area, Long minPrice,
		Long maxPrice, Double swLat, Double swLng, Double neLat, Double neLng);

	/**
	 * 정보 카드. 단지가 없으면 404.
	 */
	ResponseEntity<ComplexCardRdo> getComplex(String complexId, AreaBucket area);
}
