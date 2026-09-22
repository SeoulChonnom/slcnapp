package com.seoulchonnom.aggregate.inspection.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역명에 유니크 제약을 건다. 없으면 중복 검사가 앱 레벨 check-then-act로만 남아
 * 더블클릭에 뚫리고, 같은 생활권의 재임장 이력이 두 갈래로 갈라진다.
 */
@Entity
@Table(name = "inspection_area", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_inspection_area_name", columnNames = "name"),
	indexes = {
		@Index(name = "idx_inspection_area_name", columnList = "name")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionAreaJpo extends DomainEntityJpo {
	@Column(length = 100, nullable = false)
	private String name;
	@Column(length = 300)
	private String description;
}
