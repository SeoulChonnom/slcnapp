package com.seoulchonnom.aggregate.inspection.store.jpo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.inspection.store.jpo.converter.PropertyAnswerListConverter;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;

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
	// 계획 단계에서는 동·호수를 모를 수 있어 비워 둘 수 있다. 완료 조건(findMissingFieldsForCompletion)은 그대로다
	@Column(length = 200)
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
	// 위치는 JSON 값 객체가 아니라 일반 컬럼이다. 모두 NULL이면 위치 없음. 좌표 출처는 항상 행안부다
	@Column(length = PropertyLocation.BD_MGT_SN_MAX_LENGTH)
	private String bdMgtSn;
	@Column(length = PropertyLocation.ROAD_ADDRESS_MAX_LENGTH)
	private String roadAddress;
	private Double latitude;
	private Double longitude;
	@Column(name = "ent_x")
	private Double entX;
	@Column(name = "ent_y")
	private Double entY;
}
