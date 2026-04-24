package com.seoulchonnom.aggregate.trip.store.jpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trip_quiz_option", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_trip_quiz_option_trip_sort", columnNames = {"trip_id", "sort_order"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripQuizOptionJpo {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;
	@Column(nullable = false)
	private String text;
	@Column(nullable = false)
	private int sortOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_id")
	private TripQuizJpo quiz;
}
