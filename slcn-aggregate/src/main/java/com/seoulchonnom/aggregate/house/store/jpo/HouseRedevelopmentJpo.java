package com.seoulchonnom.aggregate.house.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_redevelopment", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_redevelopment_source_key", columnNames = "source_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseRedevelopmentJpo extends DomainEntityJpo {
	@Column(length = 100, nullable = false)
	private String sourceKey;

	@Column(length = 200, nullable = false)
	private String name;

	@Column(length = 50)
	private String projectType;

	@Column(length = 50)
	private String stage;

	private Double latitude;

	private Double longitude;

	@Column(length = 500)
	private String sourceUrl;

	private LocalDate syncedDate;
}
