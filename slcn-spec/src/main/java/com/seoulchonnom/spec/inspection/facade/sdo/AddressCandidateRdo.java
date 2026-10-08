package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressCandidateRdo {
	private String roadAddress;
	private String jibunAddress;
	private String buildingName;
	private String bdMgtSn;
	private String zipNo;
	private CoordKeySdo coordKey;
}
