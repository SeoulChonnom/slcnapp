package com.seoulchonnom.aggregate.trip.store.jpo;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trip_quiz", schema = "slcn")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripQuizJpo {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;
	@Column(nullable = false)
	private String title;
	@Column(name = "correct_option", nullable = false)
	private String correctOptionId;
	@Column(nullable = false)
	private String answerTitle;
	@Column(nullable = false)
	private String answerText;
	@Column(nullable = false)
	private String errorTitle;
	@Column(nullable = false)
	private String errorText;

	// @MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "trip_id")
	private TripJpo trip;

	@OneToMany(mappedBy = "quiz", fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	private List<TripQuizOptionJpo> options = new ArrayList<>();
}
