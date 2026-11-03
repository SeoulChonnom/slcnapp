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
 * 파생 테이블. 6단계가 DELETE + INSERT…SELECT(HouseComplexAreaStatRepository)로 통째로 다시 만든다.
 * DomainEntityJpo를 상속하지 않고 @Version도 없다. JPA로는 읽기만 하고 save하지 않는다.
 * 컬럼명은 숫자가 낀 이름이 네이밍 전략에 따라 달라지지 않도록 모두 명시한다.
 */
@Entity
@Table(name = "house_complex_area_stat", schema = "slcn")
@IdClass(HouseComplexAreaStatId.class)
@Getter
@NoArgsConstructor
public class HouseComplexAreaStatJpo {
	@Id
	@Column(name = "complex_id", nullable = false)
	private String complexId;

	/** B59 또는 B84 */
	@Id
	@Column(name = "area_bucket", length = 10, nullable = false)
	private String areaBucket;

	@Column(name = "avg_area_12m", precision = 7, scale = 2)
	private BigDecimal avgArea12m;

	@Column(name = "avg_3m")
	private Long avg3m;

	@Column(name = "cnt_3m", nullable = false)
	private int cnt3m;

	@Column(name = "avg_6m")
	private Long avg6m;

	@Column(name = "cnt_6m", nullable = false)
	private int cnt6m;

	@Column(name = "avg_12m")
	private Long avg12m;

	@Column(name = "cnt_12m", nullable = false)
	private int cnt12m;

	@Column(name = "avg_3m_1y_ago")
	private Long avg3m1yAgo;

	@Column(name = "cnt_3m_1y_ago", nullable = false)
	private int cnt3m1yAgo;

	@Column(name = "avg_3m_3y_ago")
	private Long avg3m3yAgo;

	@Column(name = "cnt_3m_3y_ago", nullable = false)
	private int cnt3m3yAgo;

	@Column(name = "volume_3m", nullable = false)
	private int volume3m;

	@Column(name = "recent_price_manwon")
	private Long recentPriceManwon;

	/** 3, 6, 또는 null */
	@Column(name = "recent_window_months")
	private Integer recentWindowMonths;

	@Column(name = "recent_sample", nullable = false)
	private int recentSample;

	@Column(name = "change_1y_pct", precision = 9, scale = 2)
	private BigDecimal change1yPct;

	@Column(name = "change_1y_manwon")
	private Long change1yManwon;

	@Column(name = "change_3y_pct", precision = 9, scale = 2)
	private BigDecimal change3yPct;

	@Column(name = "change_3y_manwon")
	private Long change3yManwon;

	@Column(name = "computed_date", nullable = false)
	private LocalDate computedDate;
}
