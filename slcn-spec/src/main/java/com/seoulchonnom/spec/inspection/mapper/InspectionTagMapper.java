package com.seoulchonnom.spec.inspection.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;

@Component
public class InspectionTagMapper {
	/**
	 * 앞의 #을 떼고 연속 공백을 하나로 줄인다. 유니크 판정은 이 결과를 기준으로 한다.
	 */
	public String normalizeName(String rawName) {
		if (rawName == null) {
			return null;
		}
		String normalized = rawName.trim();
		while (normalized.startsWith("#")) {
			normalized = normalized.substring(1).trim();
		}
		normalized = normalized.replaceAll("\\s+", " ");
		return normalized.isEmpty() ? null : normalized;
	}

	public InspectionTag toInspectionTag(String rawName) {
		String name = normalizeName(rawName);
		return name == null ? null : new InspectionTag(name);
	}

	public InspectionTagRdo toInspectionTagRdo(InspectionTag tag, int usageCount) {
		return new InspectionTagRdo(tag.getId(), tag.getName(), usageCount);
	}
}
