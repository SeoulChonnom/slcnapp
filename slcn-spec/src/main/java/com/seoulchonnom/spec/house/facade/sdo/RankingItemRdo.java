package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.RankingBadge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 랭킹 한 행. 구별 대장의 "거래 없음" 행(badge NO_TRADE)은 lawdCd·districtName 외 단지 관련 필드와 value가 모두 null이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RankingItemRdo {
	private int rank;
	private String complexId;
	private String name;
	private String lawdCd;
	private String districtName;
	/** 좌표 보강에 실패한 단지는 null */
	private Double latitude;
	private Double longitude;
	private Integer buildYear;
	/** 최근 12개월 해당 평형 평균 전용면적. 통계가 없으면 null */
	private BigDecimal avgExclusiveArea;
	private Integer households;
	/** LEADER = 12개월 평균가(만원), RISE/FALL = 1년 변동률(%), VOLUME = 3개월 거래 건수 */
	private BigDecimal value;
	/** 1년 등락가격(만원). 계산할 수 없으면 null */
	private Long changeManwon;
	private int sample;
	private RankingBadge badge;
}
