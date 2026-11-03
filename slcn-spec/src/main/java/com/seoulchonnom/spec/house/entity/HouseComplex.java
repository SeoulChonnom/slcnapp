package com.seoulchonnom.spec.house.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.FarSource;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 아파트 단지. 실거래에 처음 나온 aptSeq로 만들고, 좌표와 건축물대장 값은 이후 단계가 채운다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseComplex extends DomainEntity {
	/** 실거래 단지 일련번호. 비어 있던 행은 ALT:{sggCd}{umdCd}{bonbun}{bubun}:{정규화명 해시 8자} */
	private String aptSeq;
	private String lawdCd;
	private String umdCd;
	private String umdName;
	/** 0 채움 4자리. 건축물대장 조회 키 */
	private String bonbun;
	private String bubun;
	private String aptName;
	private String roadName;
	private String roadBonbun;
	private String roadBubun;
	private String roadAddress;
	private AddressOverride addressOverride;
	private Double latitude;
	private Double longitude;
	private GeocodeStatus geocodeStatus;
	private int geocodeAttempts;
	private RegisterStatus registerStatus;
	private int registerAttempts;
	private LocalDate registerSyncedDate;
	private Integer buildYear;
	private LocalDate approvalDate;
	private Integer households;
	private BigDecimal farPct;
	private BigDecimal bcrPct;
	private FarSource farSource;
}
