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

	/**
	 * 화면에 보이는 연식 기준 연도. 건축물대장 사용승인일의 연도가 우선이고, 없으면 실거래 건축년도다.
	 * 지도 목록·랭킹 네이티브 쿼리(HouseComplexRepository.findMapItems, HouseRankingRepository.findRankingRows)도
	 * SQL로 같은 규칙을 계산하므로 규칙을 바꾸면 함께 고친다.
	 */
	public Integer resolveBuildYear() {
		return approvalDate != null ? Integer.valueOf(approvalDate.getYear()) : buildYear;
	}
}
