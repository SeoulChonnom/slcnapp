package com.seoulchonnom.aggregate.house.store.jpo;

import java.time.LocalDate;
import java.util.Map;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.house.store.jpo.converter.JsonMapConverter;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_batch_run", schema = "slcn",
	indexes = @Index(name = "idx_house_batch_run_step", columnList = "step, status, run_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseBatchRunJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private LocalDate runDate;

	@Enumerated(EnumType.STRING)
	@Column(length = 30, nullable = false)
	private BatchStep step;

	@Enumerated(EnumType.STRING)
	@Column(length = 10, nullable = false)
	private BatchRunStatus status;

	@Column(nullable = false)
	private int apiCalls;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = JsonMapConverter.class)
	private Map<String, Object> detail;

	@Column(nullable = false)
	private long startedTime;

	@Column(nullable = false)
	private long finishedTime;

	@Column(length = 500)
	private String error;
}
