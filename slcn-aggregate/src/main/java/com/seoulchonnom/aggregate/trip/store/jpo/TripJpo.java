package com.seoulchonnom.aggregate.trip.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trip", schema = "slcn", uniqueConstraints = @UniqueConstraint(name = "uk_trip_date", columnNames = "date"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private String date;
	@Column(nullable = false)
	private String type;
	@Column(nullable = false)
	private String name;
	@Column(nullable = false)
	private String logo;
	@Column(nullable = false)
	private String firstMap;
	private String secondMap;
	private String nextButtonText;
	private String previousButtonText;
	@Column(nullable = false)
	private String driveUrl;

	@OneToOne(mappedBy = "trip", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private TripQuizJpo quiz;
}
