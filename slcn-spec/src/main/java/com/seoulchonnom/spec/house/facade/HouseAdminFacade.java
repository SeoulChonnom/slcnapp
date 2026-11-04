package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteRedevelopmentListRdo;
import com.seoulchonnom.spec.house.facade.sdo.RedevelopmentImportRdo;

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

	/**
	 * 정비사업 CSV 통째 교체. 오류가 하나라도 있으면 아무것도 저장하지 않고 400(errors[])이다.
	 */
	ResponseEntity<RedevelopmentImportRdo> importRedevelopments(MultipartFile file);

	/**
	 * 좌표가 PENDING/FAILED인 정비사업 구역. 자치구, 구역명 순.
	 *
	 * @param page 1부터. 없으면 1
	 * @param size 1~100, 없으면 20
	 */
	ResponseEntity<IncompleteRedevelopmentListRdo> getIncompleteRedevelopments(Integer page, Integer size);
}
