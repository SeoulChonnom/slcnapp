package com.seoulchonnom.aggregate.travel.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travel_photos", schema = "slcn", indexes = {
	@Index(name = "idx_travel_photos_travel_target_sort", columnList = "travel_id,travel_day_id,travel_place_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPhotoJpo extends DomainEntityJpo {
	private String travelId;
	private String travelDayId;
	private String travelPlaceId;
	private String photoFileId;
	private String caption;
	private int sortOrder;
}
