package com.seoulchonnom.aggregate.flow.inspection;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CoordKeySdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CurrentLocationSdo;
import com.seoulchonnom.spec.inspection.facade.sdo.GeoPointRdo;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 주소 검색과 현재 위치 좌표 변환. 아무것도 저장하지 않으며 외부 호출 동안 트랜잭션을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GeoQueryFlow {
	public static final int MAX_PAGE_SIZE = 20;

	private final AddressGateway addressGateway;
	private final AddressMapper addressMapper;
	private final UtmkToWgs84Converter utmkToWgs84Converter;

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

	/**
	 * 현재 위치 주소를 좌표로 바꿔 돌려주기만 한다. 현재 위치는 저장하지 않으므로 이 Flow는 저장소에 의존하지 않고,
	 * 주소와 좌표는 로그에도 남기지 않는다(예외 메시지에도 싣지 않는다).
	 */
	public GeoPointRdo getCurrentLocation(CurrentLocationSdo request) {
		CoordKeySdo coordKey = request == null ? null : request.getCoordKey();
		if (coordKey == null || !StringUtils.hasText(coordKey.getAdmCd()) || !StringUtils.hasText(coordKey.getRnMgtSn())
			|| !StringUtils.hasText(coordKey.getUdrtYn()) || !StringUtils.hasText(coordKey.getBuldMnnm())
			|| !StringUtils.hasText(coordKey.getBuldSlno())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "현재 위치의 좌표 조회 키(coordKey)가 필요합니다.");
		}
		UtmkPoint entrance = addressGateway.findEntrance(addressMapper.toCoordKey(coordKey));
		GeoPoint point = utmkToWgs84Converter.convert(entrance);
		return new GeoPointRdo(point.latitude(), point.longitude());
	}
}
