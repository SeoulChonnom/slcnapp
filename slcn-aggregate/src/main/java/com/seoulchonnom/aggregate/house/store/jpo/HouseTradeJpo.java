package com.seoulchonnom.aggregate.house.store.jpo;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.DealingType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 쓰기는 네이티브 upsert(HouseTradeRepository)로만 한다. dedup_key 유니크 제약이 없으면 ON CONFLICT가 실패한다.
 */
@Entity
@Table(name = "house_trade", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_trade_dedup_key", columnNames = "dedup_key"),
	indexes = {
		@Index(name = "idx_house_trade_stats", columnList = "complex_id, area_bucket, deal_date"),
		@Index(name = "idx_house_trade_unit", columnList = "lawd_cd, deal_ym")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseTradeJpo extends DomainEntityJpo {
	@Column(length = 64, nullable = false)
	private String dedupKey;

	@Column(nullable = false)
	private String complexId;

	@Column(length = 5, nullable = false)
	private String lawdCd;

	@Column(length = 6, nullable = false)
	private String dealYm;

	@Column(nullable = false)
	private LocalDate dealDate;

	@Column(precision = 7, scale = 2, nullable = false)
	private BigDecimal exclusiveArea;

	@Enumerated(EnumType.STRING)
	@Column(length = 10, nullable = false)
	private AreaBucket areaBucket;

	@Column(nullable = false)
	private long amountManwon;

	private Integer floor;

	@Column(length = 30)
	private String aptDong;

	private Integer buildYear;

	@Enumerated(EnumType.STRING)
	@Column(length = 10, nullable = false)
	private DealingType dealingType;

	@Column(nullable = false)
	private boolean cancelled;

	private LocalDate cancelledDate;

	@Column(nullable = false)
	private boolean outlier;

	@Column(nullable = false)
	private boolean removed;
}
