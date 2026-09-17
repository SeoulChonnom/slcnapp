package com.seoulchonnom.aggregate.inspection.store.jpo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.inspection.store.jpo.converter.PropertyAnswerListConverter;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * answers는 TEXT 컬럼이라 엔티티를 로드하면 항상 따라온다.
 * 목록·집계 경로는 ViewedPropertySummaryPdo projection으로 이 컬럼을 건너뛴다.
 */
@Entity
@Table(name = "viewed_property", schema = "slcn", indexes = {
	@Index(name = "idx_viewed_property_visit", columnList = "inspection_visit_id,sort_order"),
	@Index(name = "idx_viewed_property_interest", columnList = "interest_level"),
	@Index(name = "idx_viewed_property_complex", columnList = "complex_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewedPropertyJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private String inspectionVisitId;
	@Column(length = 200, nullable = false)
	private String complexName;
	@Column(length = 200, nullable = false)
	private String name;
	@Column(columnDefinition = "TEXT")
	private String memo;
	@Column(length = 300)
	private String oneLineReview;
	@Column(columnDefinition = "TEXT")
	private String pros;
	@Column(columnDefinition = "TEXT")
	private String cons;
	private Integer interestLevel;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InspectionStatus status;
	private int sortOrder;
	@Convert(converter = PropertyAnswerListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<PropertyAnswer> answers = new ArrayList<>();
	private int requiredAnswerCount;
	private int unansweredRequiredCount;
}
