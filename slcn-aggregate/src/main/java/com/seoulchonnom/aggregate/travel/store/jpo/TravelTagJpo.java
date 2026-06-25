package com.seoulchonnom.aggregate.travel.store.jpo;

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
@Table(name = "travel_tags", schema = "slcn", uniqueConstraints = {
	@UniqueConstraint(name = "uk_travel_tags_travel_name", columnNames = {"travel_id", "name"})
}, indexes = {
	@Index(name = "idx_travel_tags_travel_sort", columnList = "travel_id,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TravelTagJpo extends DomainEntityJpo {
	private String travelId;
	private String name;
	private int sortOrder;
}
