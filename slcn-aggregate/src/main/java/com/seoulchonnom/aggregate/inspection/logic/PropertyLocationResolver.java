package com.seoulchonnom.aggregate.inspection.logic;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.exception.InvalidPropertyLocationException;
import com.seoulchonnom.aggregate.inspection.geo.AddressGateway;
import com.seoulchonnom.aggregate.inspection.geo.AddressMapper;
import com.seoulchonnom.aggregate.inspection.geo.CoordKey;
import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkPoint;
import com.seoulchonnom.aggregate.inspection.geo.UtmkToWgs84Converter;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;
import com.seoulchonnom.spec.inspection.facade.sdo.CoordKeySdo;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyLocationInputSdo;

import lombok.RequiredArgsConstructor;

/**
 * 매물 등록·수정이 함께 쓰는 위치 판정 규칙(D9). 좌표는 요청으로 받지 않고 항상 행안부에서 조회한다.
 *
 * - 요청이 null이면 위치를 지운다.
 * - bdMgtSn이 저장된 값과 같으면 저장된 위치를 그대로 돌려주고 외부를 호출하지 않는다.
 *   표시용 도로명주소도 저장된 값을 유지한다 - 같은 건물관리번호면 같은 건물이고, 화면 문자열을
 *   요청에서 받지 않아야 저장된 위치 전체의 출처가 행안부로 일관된다.
 * - bdMgtSn이 다르거나 저장된 값이 없으면 coordKey로 좌표를 조회해 WGS84로 변환한다.
 *
 * 조회 실패(502/503)는 그대로 전파한다. 호출자는 엔티티를 바꾸기 전에 이 메서드를 불러야 한다.
 */
@Component
@RequiredArgsConstructor
public class PropertyLocationResolver {
	private final AddressGateway addressGateway;
	private final AddressMapper addressMapper;
	private final UtmkToWgs84Converter converter;

	public PropertyLocation resolve(PropertyLocation stored, PropertyLocationInputSdo request) {
		if (request == null) {
			return null;
		}
		if (!StringUtils.hasText(request.getBdMgtSn())) {
			throw new InvalidPropertyLocationException("건물관리번호(bdMgtSn)는 필수입니다.");
		}
		String bdMgtSn = request.getBdMgtSn().trim();
		String roadAddress = trimToNull(request.getRoadAddress());
		// 저장 단계(컬럼 길이 초과)의 500을 막으려고 외부 호출 전에 거절한다.
		if (bdMgtSn.length() > PropertyLocation.BD_MGT_SN_MAX_LENGTH) {
			throw new InvalidPropertyLocationException(
				"건물관리번호(bdMgtSn)는 " + PropertyLocation.BD_MGT_SN_MAX_LENGTH + "자 이하여야 합니다.");
		}
		if (roadAddress != null && roadAddress.length() > PropertyLocation.ROAD_ADDRESS_MAX_LENGTH) {
			throw new InvalidPropertyLocationException(
				"도로명주소(roadAddress)는 " + PropertyLocation.ROAD_ADDRESS_MAX_LENGTH + "자 이하여야 합니다.");
		}
		if (stored != null && bdMgtSn.equals(stored.getBdMgtSn())) {
			return stored;
		}

		CoordKey coordKey = toCoordKey(request.getCoordKey());
		UtmkPoint entrance = addressGateway.findEntrance(coordKey);
		GeoPoint point = converter.convert(entrance);
		return new PropertyLocation(bdMgtSn, roadAddress, point.latitude(),
			point.longitude(), entrance.x(), entrance.y());
	}

	private CoordKey toCoordKey(CoordKeySdo sdo) {
		if (sdo == null || !StringUtils.hasText(sdo.getAdmCd()) || !StringUtils.hasText(sdo.getRnMgtSn())
			|| !StringUtils.hasText(sdo.getUdrtYn()) || !StringUtils.hasText(sdo.getBuldMnnm())
			|| !StringUtils.hasText(sdo.getBuldSlno())) {
			throw new InvalidPropertyLocationException("새 위치를 저장하려면 좌표 조회 키(coordKey)가 필요합니다.");
		}
		return addressMapper.toCoordKey(sdo);
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
