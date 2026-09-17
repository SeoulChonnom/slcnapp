package com.seoulchonnom.aggregate.inspection.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionTagJpo;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;

@Component
public class InspectionTagJpoMapper {
	public InspectionTagJpo toJpo(InspectionTag tag) {
		InspectionTagJpo jpo = new InspectionTagJpo(tag.getName());
		jpo.setId(tag.getId());
		jpo.setEntityVersion(tag.getEntityVersion());
		jpo.setRegisteredTime(tag.getRegisteredTime());
		jpo.setModifiedTime(tag.getModifiedTime());
		return jpo;
	}

	public InspectionTag toDomain(InspectionTagJpo jpo) {
		InspectionTag tag = InspectionTag.builder()
			.name(jpo.getName())
			.build();
		tag.setId(jpo.getId());
		tag.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			tag.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			tag.setModifiedTime(jpo.getModifiedTime());
		}
		return tag;
	}
}
