package com.seoulchonnom.aggregate.inspection.store.jpo;

import java.time.LocalDateTime;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import jakarta.persistence.Column;
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
@Table(name = "inspection_visit", schema = "slcn", indexes = {
	@Index(name = "idx_inspection_visit_area_visited", columnList = "area_id,visited_at"),
	@Index(name = "idx_inspection_visit_visited", columnList = "visited_at"),
	@Index(name = "idx_inspection_visit_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionVisitJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private String areaId;
	@Column(nullable = false)
	private LocalDateTime visitedAt;
	@Column(columnDefinition = "TEXT")
	private String memo;
	@Enumerated(EnumType.STRING)
	private RevisitIntent revisitIntent;
	@Column(length = 300)
	private String oneLineReview;
	@Column(columnDefinition = "TEXT")
	private String pros;
	@Column(columnDefinition = "TEXT")
	private String cons;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InspectionStatus status;
}
