package com.seoulchonnom.spec.inspection.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.IncompleteSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaBriefRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyBriefRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;

@Component
public class InspectionVisitMapper {
	public InspectionVisit toInspectionVisit(String id, String areaId, InspectionVisitCdo cdo) {
		InspectionVisit visit = new InspectionVisit(id, areaId, toLocalDateTime(cdo.getVisitedAt()));
		visit.setMemo(cdo.getMemo());
		visit.setRevisitIntent(cdo.getRevisitIntent());
		visit.setOneLineReview(cdo.getOneLineReview());
		visit.setPros(cdo.getPros());
		visit.setCons(cdo.getCons());
		visit.setStatus(InspectionStatus.DRAFT);
		return visit;
	}

	/**
	 * topInterestProperty는 이미 만들어진 Rdo로 받는다. 목록 조립이 엔티티가 아니라
	 * projection으로 매물을 읽으므로 여기서 엔티티를 요구하면 변환이 두 번 일어난다.
	 */
	public InspectionVisitRdo toInspectionVisitRdo(InspectionVisit visit, InspectionArea area, int propertyCount,
		ViewedPropertyBriefRdo topInterestProperty, List<String> tags, FileBoxItemRdo cover,
		IncompleteSummaryRdo incompleteSummary) {
		InspectionVisitRdo rdo = new InspectionVisitRdo();
		rdo.setInspectionVisitId(visit.getId());
		rdo.setArea(toInspectionAreaBriefRdo(area));
		rdo.setVisitedAt(toText(visit.getVisitedAt()));
		rdo.setOneLineReview(visit.getOneLineReview());
		rdo.setRevisitIntent(visit.getRevisitIntent());
		rdo.setStatus(visit.getStatus());
		rdo.setPropertyCount(propertyCount);
		rdo.setTopInterestProperty(topInterestProperty);
		rdo.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
		rdo.setCover(cover);
		rdo.setIncompleteSummary(incompleteSummary);
		return rdo;
	}

	/**
	 * 지역 목록의 latestVisit과 지역 상세의 visits[]가 함께 쓴다.
	 * latestVisit에서는 propertyCount/incompleteSummary/cover를 null로 넘긴다.
	 */
	public InspectionVisitSummaryRdo toInspectionVisitSummaryRdo(InspectionVisit visit, List<String> tags,
		Integer propertyCount, IncompleteSummaryRdo incompleteSummary, FileBoxItemRdo cover) {
		InspectionVisitSummaryRdo rdo = new InspectionVisitSummaryRdo();
		rdo.setVisitId(visit.getId());
		rdo.setVisitedAt(toText(visit.getVisitedAt()));
		rdo.setOneLineReview(visit.getOneLineReview());
		rdo.setRevisitIntent(visit.getRevisitIntent());
		rdo.setStatus(visit.getStatus());
		rdo.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
		rdo.setPropertyCount(propertyCount);
		rdo.setIncompleteSummary(incompleteSummary);
		rdo.setCover(cover);
		return rdo;
	}

	/**
	 * properties는 sortOrder 오름차순 평면 배열로 받는다. 단지별 그룹핑은 FE가 한다.
	 */
	public InspectionVisitDetailRdo toInspectionVisitDetailRdo(InspectionVisit visit, InspectionArea area,
		List<String> tags, List<ViewedPropertyDetailRdo> properties, List<FileBoxItemRdo> files,
		IncompleteSummaryRdo incompleteSummary) {
		List<FileBoxItemRdo> fileItems = files == null ? List.of() : files;
		InspectionVisitDetailRdo rdo = new InspectionVisitDetailRdo();
		rdo.setInspectionVisitId(visit.getId());
		rdo.setArea(toInspectionAreaBriefRdo(area));
		rdo.setVisitedAt(toText(visit.getVisitedAt()));
		rdo.setMemo(visit.getMemo());
		rdo.setRevisitIntent(visit.getRevisitIntent());
		rdo.setOneLineReview(visit.getOneLineReview());
		rdo.setPros(visit.getPros());
		rdo.setCons(visit.getCons());
		rdo.setStatus(visit.getStatus());
		rdo.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
		rdo.setProperties(properties == null ? new ArrayList<>() : new ArrayList<>(properties));
		rdo.setCover(ViewedPropertyMapper.coverOf(fileItems, FileBoxTargetType.INSPECTION_VISIT, null));
		rdo.setPhotos(ViewedPropertyMapper.photosOf(fileItems, FileBoxTargetType.INSPECTION_VISIT, null));
		rdo.setIncompleteSummary(incompleteSummary);
		return rdo;
	}

	public InspectionAreaBriefRdo toInspectionAreaBriefRdo(InspectionArea area) {
		if (area == null) {
			return null;
		}
		return new InspectionAreaBriefRdo(area.getId(), area.getName());
	}

	static String toText(LocalDateTime value) {
		return value == null ? null : value.toString();
	}

	static LocalDateTime toLocalDateTime(String value) {
		return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
	}
}
