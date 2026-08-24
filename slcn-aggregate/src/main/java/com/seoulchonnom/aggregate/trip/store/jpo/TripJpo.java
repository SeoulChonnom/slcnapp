package com.seoulchonnom.aggregate.trip.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.converter.QuizConverter;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.entity.vo.TripType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "trip", schema = "slcn", uniqueConstraints = @UniqueConstraint(name = "uk_trip_date", columnNames = "date"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private String date;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TripType type;
	@Column(nullable = false)
	private String name;
	private String nextButtonText;
	private String previousButtonText;
	@Column(nullable = false)
	private String driveUrl;

	@Convert(converter = QuizConverter.class)
	@Column(columnDefinition = "TEXT")
	private Quiz quiz;
}
