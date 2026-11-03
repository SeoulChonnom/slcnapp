package com.seoulchonnom.aggregate.house.store.jpo;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파생 테이블. 6단계가 DELETE + INSERT(HouseRankingRepository)로 통째로 다시 만든다.
 * 통계 테이블과 같이 DomainEntityJpo·@Version 없이 JPA로는 스키마 정의와 읽기 용도로만 둔다.
 * complex_id·value_num은 구별 대장의 "거래 없음" 행에서 null이다.
 */
@Entity
@Table(name = "house_ranking", schema = "slcn")
@IdClass(HouseRankingId.class)
@Getter
@NoArgsConstructor
public class HouseRankingJpo {
	@Id
	@Column(name = "ranking_type", length = 10, nullable = false)
	private String rankingType;

	@Id
	@Column(name = "area_bucket", length = 10, nullable = false)
	private String areaBucket;

	@Id
	@Column(name = "rank_no", nullable = false)
	private int rankNo;

	@Column(name = "lawd_cd", length = 5, nullable = false)
	private String lawdCd;

	@Column(name = "complex_id")
	private String complexId;

	@Column(name = "value_num", precision = 12, scale = 2)
	private BigDecimal valueNum;

	@Column(name = "change_manwon")
	private Long changeManwon;

	@Column(name = "sample_count", nullable = false)
	private int sampleCount;

	/** LOW_SAMPLE, NO_TRADE 또는 null */
	@Column(name = "badge", length = 20)
	private String badge;

	@Column(name = "period_from", nullable = false)
	private LocalDate periodFrom;

	@Column(name = "period_to", nullable = false)
	private LocalDate periodTo;

	@Column(name = "computed_date", nullable = false)
	private LocalDate computedDate;
}
