package com.seoulchonnom.aggregate.travel.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

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
	private String coverPhotoId;
	private String oneLineReview;
	private boolean hidden;
}
