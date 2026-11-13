package com.seoulchonnom.spec.house.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.DealingType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 아파트 매매 실거래 한 건. 원천에 거래 고유 ID가 없어 dedupKey로 같은 거래를 알아본다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseTrade extends DomainEntity {
	/** SHA-256 hex 64자 */
	private String dedupKey;
	private String complexId;
	/** 수집 단위 키. 원천 삭제 판정 범위다. */
	private String lawdCd;
	private String dealYm;
	private LocalDate dealDate;
	private BigDecimal exclusiveArea;
	private AreaBucket areaBucket;
	private long amountManwon;
	/** 지하는 음수, 빈 값은 null */
	private Integer floor;
	private String aptDong;
	private Integer buildYear;
	private DealingType dealingType;
	private boolean cancelled;
	private LocalDate cancelledDate;
	/** 통계 단계가 매번 다시 판정한다. */
	private boolean outlier;
	/** 원천에서 사라진 거래 */
	private boolean removed;
}
