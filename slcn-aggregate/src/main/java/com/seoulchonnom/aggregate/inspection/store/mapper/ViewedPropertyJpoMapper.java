package com.seoulchonnom.aggregate.inspection.store.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyJpo;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;

@Component
public class ViewedPropertyJpoMapper {
	public ViewedPropertyJpo toJpo(ViewedProperty property) {
		ViewedPropertyJpo jpo = new ViewedPropertyJpo(
			property.getInspectionVisitId(),
			property.getComplexName(),
			property.getName(),
			property.getMemo(),
			property.getOneLineReview(),
			property.getPros(),
			property.getCons(),
			property.getInterestLevel(),
			property.getStatus(),
			property.getSortOrder(),
			property.getAnswers() == null ? new ArrayList<>() : new ArrayList<>(property.getAnswers()),
			property.getRequiredAnswerCount(),
			property.getUnansweredRequiredCount(),
			null, null, null, null, null, null
		);
		applyLocation(jpo, property.getLocation());
		jpo.setId(property.getId());
		jpo.setEntityVersion(property.getEntityVersion());
		jpo.setRegisteredTime(property.getRegisteredTime());
		jpo.setModifiedTime(property.getModifiedTime());
		return jpo;
	}

	public ViewedProperty toDomain(ViewedPropertyJpo jpo) {
		ViewedProperty property = ViewedProperty.builder()
			.inspectionVisitId(jpo.getInspectionVisitId())
			.complexName(jpo.getComplexName())
			.name(jpo.getName())
			.memo(jpo.getMemo())
			.oneLineReview(jpo.getOneLineReview())
			.pros(jpo.getPros())
			.cons(jpo.getCons())
			.interestLevel(jpo.getInterestLevel())
			.status(jpo.getStatus())
			.sortOrder(jpo.getSortOrder())
			.answers(jpo.getAnswers() == null ? new ArrayList<>() : new ArrayList<>(jpo.getAnswers()))
			.requiredAnswerCount(jpo.getRequiredAnswerCount())
			.unansweredRequiredCount(jpo.getUnansweredRequiredCount())
			.location(toLocation(jpo))
			.build();
		property.setId(jpo.getId());
		property.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			property.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			property.setModifiedTime(jpo.getModifiedTime());
		}
		return property;
	}

	private void applyLocation(ViewedPropertyJpo jpo, PropertyLocation location) {
		if (location == null) {
			return;
		}
		jpo.setBdMgtSn(location.getBdMgtSn());
		jpo.setRoadAddress(location.getRoadAddress());
		jpo.setLatitude(location.getLatitude());
		jpo.setLongitude(location.getLongitude());
		jpo.setEntX(location.getEntX());
		jpo.setEntY(location.getEntY());
	}

	/**
	 * 여섯 컬럼이 모두 NULL이면 위치 없음이다. 건물관리번호가 없으면 위치로 쓸 수 없으므로 없음으로 본다.
	 */
	private PropertyLocation toLocation(ViewedPropertyJpo jpo) {
		if (jpo.getBdMgtSn() == null || jpo.getLatitude() == null || jpo.getLongitude() == null
			|| jpo.getEntX() == null || jpo.getEntY() == null) {
			return null;
		}
		return new PropertyLocation(jpo.getBdMgtSn(), jpo.getRoadAddress(), jpo.getLatitude(), jpo.getLongitude(),
			jpo.getEntX(), jpo.getEntY());
	}
}
