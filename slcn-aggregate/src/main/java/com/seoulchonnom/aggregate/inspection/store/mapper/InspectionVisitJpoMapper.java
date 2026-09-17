package com.seoulchonnom.aggregate.inspection.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitJpo;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;

@Component
public class InspectionVisitJpoMapper {
	public InspectionVisitJpo toJpo(InspectionVisit visit) {
		InspectionVisitJpo jpo = new InspectionVisitJpo(
			visit.getAreaId(),
			visit.getVisitedAt(),
			visit.getMemo(),
			visit.getRevisitIntent(),
			visit.getOneLineReview(),
			visit.getPros(),
			visit.getCons(),
			visit.getStatus()
		);
		jpo.setId(visit.getId());
		jpo.setEntityVersion(visit.getEntityVersion());
		jpo.setRegisteredTime(visit.getRegisteredTime());
		jpo.setModifiedTime(visit.getModifiedTime());
		return jpo;
	}

	public InspectionVisit toDomain(InspectionVisitJpo jpo) {
		InspectionVisit visit = InspectionVisit.builder()
			.areaId(jpo.getAreaId())
			.visitedAt(jpo.getVisitedAt())
			.memo(jpo.getMemo())
			.revisitIntent(jpo.getRevisitIntent())
			.oneLineReview(jpo.getOneLineReview())
			.pros(jpo.getPros())
			.cons(jpo.getCons())
			.status(jpo.getStatus())
			.build();
		visit.setId(jpo.getId());
		visit.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			visit.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			visit.setModifiedTime(jpo.getModifiedTime());
		}
		return visit;
	}
}
