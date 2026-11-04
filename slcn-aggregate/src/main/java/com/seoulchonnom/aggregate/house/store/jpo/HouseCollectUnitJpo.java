package com.seoulchonnom.aggregate.house.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_collect_unit", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_collect_unit", columnNames = {"lawd_cd", "deal_ym"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseCollectUnitJpo extends DomainEntityJpo {
	@Column(length = 5, nullable = false)
	private String lawdCd;

	@Column(length = 6, nullable = false)
	private String dealYm;

	@Enumerated(EnumType.STRING)
	@Column(length = 10, nullable = false)
	private CollectUnitStatus status;

	private LocalDate lastSuccessDate;

	@Column(nullable = false)
	private int attempts;
}
