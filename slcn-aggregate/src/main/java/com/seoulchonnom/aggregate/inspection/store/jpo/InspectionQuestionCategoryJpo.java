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
 * 이름에 유니크 제약을 건다. 비활성 분류까지 포함해 전체에서 이름 중복을 금지하므로(계획 §0-6)
 * enabled와 무관하게 걸어야 한다.
 */
@Entity
@Table(name = "inspection_question_category", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_inspection_question_category_name", columnNames = "name"),
	indexes = {
		@Index(name = "idx_inspection_question_category_enabled_sort", columnList = "enabled,sort_order")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionCategoryJpo extends DomainEntityJpo {
	@Column(length = 50, nullable = false)
	private String name;
	private int sortOrder;
	private boolean enabled;
}
