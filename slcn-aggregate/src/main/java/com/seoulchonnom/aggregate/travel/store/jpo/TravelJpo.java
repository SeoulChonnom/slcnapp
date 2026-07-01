package com.seoulchonnom.aggregate.travel.store.jpo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.travel.store.jpo.converter.StringListConverter;
import com.seoulchonnom.aggregate.travel.store.jpo.converter.TravelDayListConverter;
import com.seoulchonnom.aggregate.travel.store.jpo.converter.TravelReviewConverter;
import com.seoulchonnom.spec.travel.entity.vo.TravelDay;
import com.seoulchonnom.spec.travel.entity.vo.TravelReview;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travels", schema = "slcn", indexes = {
	@Index(name = "idx_travels_hidden_start_date", columnList = "hidden,start_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelJpo extends DomainEntityJpo {
	private String title;
	private String region;
	private LocalDate startDate;
	private LocalDate endDate;
	private boolean hidden;
	@Convert(converter = TravelDayListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<TravelDay> days = new ArrayList<>();
	@Convert(converter = StringListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<String> tags = new ArrayList<>();
	@Convert(converter = TravelReviewConverter.class)
	@Column(columnDefinition = "TEXT")
	private TravelReview review;
}
