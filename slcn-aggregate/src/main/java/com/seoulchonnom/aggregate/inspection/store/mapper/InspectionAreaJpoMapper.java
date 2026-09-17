package com.seoulchonnom.aggregate.inspection.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionAreaJpo;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;

@Component
public class InspectionAreaJpoMapper {
	public InspectionAreaJpo toJpo(InspectionArea area) {
		InspectionAreaJpo jpo = new InspectionAreaJpo(area.getName(), area.getDescription(), area.isHidden());
		jpo.setId(area.getId());
		jpo.setEntityVersion(area.getEntityVersion());
		jpo.setRegisteredTime(area.getRegisteredTime());
		jpo.setModifiedTime(area.getModifiedTime());
		return jpo;
	}

	public InspectionArea toDomain(InspectionAreaJpo jpo) {
		InspectionArea area = InspectionArea.builder()
			.name(jpo.getName())
			.description(jpo.getDescription())
			.hidden(jpo.isHidden())
			.build();
		area.setId(jpo.getId());
		area.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			area.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			area.setModifiedTime(jpo.getModifiedTime());
		}
		return area;
	}
}
