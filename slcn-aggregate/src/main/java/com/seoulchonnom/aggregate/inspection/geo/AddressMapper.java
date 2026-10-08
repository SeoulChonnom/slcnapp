package com.seoulchonnom.aggregate.inspection.geo;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.inspection.facade.sdo.AddressCandidateRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CoordKeySdo;

/**
 * 도메인 값 타입 -> 공개 DTO. 도메인이 DTO에 의존하지 않도록 방향은 이쪽 하나뿐이다.
 */
@Component
public class AddressMapper {
	public AddressSearchRdo toAddressSearchRdo(AddressSearchResult result) {
		return new AddressSearchRdo(result.totalCount(),
			result.candidates().stream().map(this::toAddressCandidateRdo).toList());
	}

	public AddressCandidateRdo toAddressCandidateRdo(AddressCandidate candidate) {
		return new AddressCandidateRdo(candidate.roadAddress(), candidate.jibunAddress(), candidate.buildingName(),
			candidate.bdMgtSn(), candidate.zipNo(), toCoordKeySdo(candidate.coordKey()));
	}

	public CoordKeySdo toCoordKeySdo(CoordKey key) {
		return new CoordKeySdo(key.admCd(), key.rnMgtSn(), key.udrtYn(), key.buldMnnm(), key.buldSlno());
	}

	public CoordKey toCoordKey(CoordKeySdo sdo) {
		return new CoordKey(sdo.getAdmCd(), sdo.getRnMgtSn(), sdo.getUdrtYn(), sdo.getBuldMnnm(), sdo.getBuldSlno());
	}
}
