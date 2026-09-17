package com.seoulchonnom.spec.inspection.entity;

import java.time.LocalDateTime;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 한 지역을 한 번 다녀온 기록(회차).
 * revisitIntent는 요구사항상 필수지만 DRAFT 부분 저장을 허용해야 하므로
 * 저장 제약이 아니라 완료 조건으로 다룬다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InspectionVisit extends DomainEntity {
	private String areaId;
	private LocalDateTime visitedAt;
	private String memo;
	private RevisitIntent revisitIntent;
	private String oneLineReview;
	private String pros;
	private String cons;
	private InspectionStatus status;

	public InspectionVisit(String id, String areaId, LocalDateTime visitedAt) {
		super(id);
		this.areaId = areaId;
		this.visitedAt = visitedAt;
		this.status = InspectionStatus.DRAFT;
	}

	public void update(LocalDateTime visitedAt, String memo, RevisitIntent revisitIntent, String oneLineReview,
		String pros, String cons) {
		this.visitedAt = visitedAt;
		this.memo = memo;
		this.revisitIntent = revisitIntent;
		this.oneLineReview = oneLineReview;
		this.pros = pros;
		this.cons = cons;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeStatus(InspectionStatus status) {
		this.status = status;
		this.modifiedTime = System.currentTimeMillis();
	}
}
