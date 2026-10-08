package com.seoulchonnom.aggregate.flow.inspection;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;

import lombok.RequiredArgsConstructor;

/**
 * 주소 검색. 아무것도 저장하지 않으며 외부 호출 동안 트랜잭션을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GeoQueryFlow {
	public static final int MAX_PAGE_SIZE = 20;

	private final AddressGateway addressGateway;
	private final AddressMapper addressMapper;

	public AddressSearchRdo searchAddresses(String keyword, int page, int size) {
		String trimmed = keyword == null ? "" : keyword.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "검색어를 입력하세요.");
		}
		if (page < 1) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page는 1 이상이어야 합니다.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
		}
		return addressMapper.toAddressSearchRdo(addressGateway.search(trimmed, page, size));
	}
}
