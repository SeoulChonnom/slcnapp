package com.seoulchonnom.aggregate.house.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

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
@Table(name = "house_school", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_school_code", columnNames = "school_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseSchoolJpo extends DomainEntityJpo {
	@Column(length = 30, nullable = false)
	private String schoolCode;

	@Column(length = 100, nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(length = 5, nullable = false)
	private SchoolLevel level;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	private LocalDate syncedDate;
}
