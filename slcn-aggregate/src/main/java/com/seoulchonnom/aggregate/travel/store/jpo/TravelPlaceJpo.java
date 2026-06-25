package com.seoulchonnom.aggregate.travel.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.travel.entity.vo.TravelPlaceCategory;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travel_places", schema = "slcn", indexes = {
	@Index(name = "idx_travel_places_travel_day_sort", columnList = "travel_id,travel_day_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlaceJpo extends DomainEntityJpo {
	private String travelId;
	private String travelDayId;
	private String name;
	@Enumerated(EnumType.STRING)
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private String coverPhotoId;
	private int sortOrder;
}
