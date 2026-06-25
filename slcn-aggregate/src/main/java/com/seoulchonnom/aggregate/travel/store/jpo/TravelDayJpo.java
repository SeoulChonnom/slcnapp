package com.seoulchonnom.aggregate.travel.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travel_days", schema = "slcn", uniqueConstraints = {
	@UniqueConstraint(name = "uk_travel_days_travel_date", columnNames = {"travel_id", "date"})
}, indexes = {
	@Index(name = "idx_travel_days_travel_sort", columnList = "travel_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelDayJpo extends DomainEntityJpo {
	private String travelId;
	private LocalDate date;
	private String title;
	private String memo;
	private String coverPhotoId;
	private int dayNumber;
	private int sortOrder;
}
