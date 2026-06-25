package com.seoulchonnom.aggregate.trip.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.converter.QuizConverter;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
	@Column(name = "logo_file_id", nullable = false)
	private String logoFileId;
	@Column(name = "first_map_file_id", nullable = false)
	private String firstMapFileId;
	@Column(name = "second_map_file_id")
	private String secondMapFileId;
	private String nextButtonText;
	private String previousButtonText;
	@Column(nullable = false)
	private String driveUrl;

	@Convert(converter = QuizConverter.class)
	@Column(columnDefinition = "TEXT")
	private Quiz quiz;
}
