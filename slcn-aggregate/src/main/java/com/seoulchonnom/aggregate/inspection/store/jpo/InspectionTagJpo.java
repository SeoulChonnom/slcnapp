package com.seoulchonnom.aggregate.inspection.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inspection_tag", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_inspection_tag_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionTagJpo extends DomainEntityJpo {
	@Column(length = 50, nullable = false)
	private String name;
}
