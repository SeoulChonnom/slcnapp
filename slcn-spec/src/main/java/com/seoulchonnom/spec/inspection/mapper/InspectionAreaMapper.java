package com.seoulchonnom.spec.inspection.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.facade.sdo.IncompleteSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaTotalsRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.RevisitIntentCountsRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyBriefRdo;

@Component
public class InspectionAreaMapper {
	public InspectionArea toInspectionArea(String id, InspectionAreaCdo cdo) {
		return new InspectionArea(id, cdo.getName(), cdo.getDescription());
	}

	/**
	 * 집계 값은 전부 호출자가 계산해 넘긴다. 지역 엔티티는 집계를 저장하지 않는다.
	 *
	 * @param topProperty 지역 전체에서 고른 최고 관심 매물. 회차 범위가 아니다
	 */
	public InspectionAreaRdo toInspectionAreaRdo(InspectionArea area, int visitCount, LocalDateTime firstVisitedAt,
		LocalDateTime lastVisitedAt, int totalPropertyCount, InspectionVisitSummaryRdo latestVisit,
		ViewedPropertyBriefRdo topProperty, IncompleteSummaryRdo incompleteSummary,
		List<FileBoxItemRdo> thumbnails, int totalImageCount) {
		InspectionAreaRdo rdo = new InspectionAreaRdo();
		rdo.setAreaId(area.getId());
		rdo.setName(area.getName());
		rdo.setDescription(area.getDescription());
		rdo.setVisitCount(visitCount);
		rdo.setFirstVisitedAt(InspectionVisitMapper.toText(firstVisitedAt));
		rdo.setLastVisitedAt(InspectionVisitMapper.toText(lastVisitedAt));
		rdo.setTotalPropertyCount(totalPropertyCount);
		rdo.setLatestVisit(latestVisit);
		rdo.setTopProperty(topProperty);
		rdo.setIncompleteSummary(incompleteSummary);
		rdo.setThumbnails(thumbnails == null ? new ArrayList<>() : new ArrayList<>(thumbnails));
		rdo.setTotalImageCount(totalImageCount);
		return rdo;
	}

	/**
	 * GET /inspection-areas 응답 조립. revisitIntentCounts/totals는 필터 무관 전역 값이라
	 * items/totalCount/hasNext와 별개로 호출자가 이미 계산해 넘긴다.
	 */
	public InspectionAreaListRdo toInspectionAreaListRdo(List<InspectionAreaRdo> items, long totalCount,
		boolean hasNext, RevisitIntentCountsRdo revisitIntentCounts, InspectionAreaTotalsRdo totals) {
		InspectionAreaListRdo rdo = new InspectionAreaListRdo();
		rdo.setItems(items == null ? new ArrayList<>() : new ArrayList<>(items));
		rdo.setTotalCount(totalCount);
		rdo.setHasNext(hasNext);
		rdo.setRevisitIntentCounts(revisitIntentCounts);
		rdo.setTotals(totals);
		return rdo;
	}

	public InspectionAreaDetailRdo toInspectionAreaDetailRdo(InspectionAreaRdo area,
		List<InspectionVisitSummaryRdo> visits, boolean hasMoreVisits, int visitPageSize,
		InspectionVisitDetailRdo selectedVisit) {
		InspectionAreaDetailRdo rdo = new InspectionAreaDetailRdo();
		rdo.setArea(area);
		rdo.setVisits(visits == null ? new ArrayList<>() : new ArrayList<>(visits));
		rdo.setHasMoreVisits(hasMoreVisits);
		rdo.setVisitPageSize(visitPageSize);
		rdo.setSelectedVisit(selectedVisit);
		return rdo;
	}
}
