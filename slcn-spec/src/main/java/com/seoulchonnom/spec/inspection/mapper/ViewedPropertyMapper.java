package com.seoulchonnom.spec.inspection.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;
import com.seoulchonnom.spec.inspection.facade.sdo.IncompleteSummaryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyLocationRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyBriefRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.util.InspectionQuestionOrdering;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ViewedPropertyMapper {
	private final PropertyAnswerMapper propertyAnswerMapper;

	public ViewedProperty toViewedProperty(String visitId, ViewedPropertyCdo cdo, int sortOrder) {
		ViewedProperty property = new ViewedProperty(visitId, cdo.getComplexName(), cdo.getName(), sortOrder);
		property.setMemo(cdo.getMemo());
		property.setOneLineReview(cdo.getOneLineReview());
		property.setPros(cdo.getPros());
		property.setCons(cdo.getCons());
		property.setInterestLevel(cdo.getInterestLevel());
		return property;
	}

	public ViewedPropertyRdo toViewedPropertyRdo(ViewedProperty property, List<String> tags,
		List<FileBoxItemRdo> files) {
		List<FileBoxItemRdo> fileItems = files == null ? List.of() : files;
		ViewedPropertyRdo rdo = new ViewedPropertyRdo();
		rdo.setPropertyId(property.getId());
		rdo.setInspectionVisitId(property.getInspectionVisitId());
		rdo.setComplexName(property.getComplexName());
		rdo.setName(property.getName());
		rdo.setMemo(property.getMemo());
		rdo.setOneLineReview(property.getOneLineReview());
		rdo.setPros(property.getPros());
		rdo.setCons(property.getCons());
		rdo.setInterestLevel(property.getInterestLevel());
		rdo.setStatus(property.getStatus());
		rdo.setSortOrder(property.getSortOrder());
		rdo.setLocation(toPropertyLocationRdo(property.getLocation()));
		rdo.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
		rdo.setCover(coverOf(fileItems, FileBoxTargetType.VIEWED_PROPERTY, property.getId()));
		rdo.setPhotos(photosOf(fileItems, FileBoxTargetType.VIEWED_PROPERTY, property.getId()));
		return rdo;
	}

	/**
	 * 문답은 매물 안에 있으므로 따로 받지 않는다.
	 * questions는 배지 계산용이며 비어 있어도 문답 렌더링은 스냅샷으로 정상 동작한다.
	 *
	 * areaId/areaName/visitedAt과 prevProperty/nextProperty는 호출자가 이미 로드한
	 * 임장/지역/매물 목록에서 계산해 넘긴다. 여기서 추가로 조회하지 않는다.
	 */
	public ViewedPropertyDetailRdo toViewedPropertyDetailRdo(ViewedProperty property, List<String> tags,
		List<FileBoxItemRdo> files, Map<String, InspectionQuestion> questions,
		IncompleteSummaryRdo incompleteSummary, String areaId, String areaName, String visitedAt,
		ViewedPropertyBriefRdo prevProperty, ViewedPropertyBriefRdo nextProperty) {
		ViewedPropertyRdo base = toViewedPropertyRdo(property, tags, files);
		Map<String, InspectionQuestion> questionMap = questions == null ? Map.of() : questions;

		ViewedPropertyDetailRdo detailRdo = new ViewedPropertyDetailRdo();
		detailRdo.setPropertyId(base.getPropertyId());
		detailRdo.setInspectionVisitId(base.getInspectionVisitId());
		detailRdo.setAreaId(areaId);
		detailRdo.setAreaName(areaName);
		detailRdo.setVisitedAt(visitedAt);
		detailRdo.setComplexName(base.getComplexName());
		detailRdo.setName(base.getName());
		detailRdo.setMemo(base.getMemo());
		detailRdo.setOneLineReview(base.getOneLineReview());
		detailRdo.setPros(base.getPros());
		detailRdo.setCons(base.getCons());
		detailRdo.setInterestLevel(base.getInterestLevel());
		detailRdo.setStatus(base.getStatus());
		detailRdo.setSortOrder(base.getSortOrder());
		detailRdo.setLocation(base.getLocation());
		detailRdo.setTags(base.getTags());
		detailRdo.setCover(base.getCover());
		detailRdo.setPhotos(base.getPhotos());
		detailRdo.setAnswers(toPropertyAnswerRdos(property.getAnswers(), questionMap));
		detailRdo.setIncompleteSummary(incompleteSummary);
		detailRdo.setPrevProperty(prevProperty);
		detailRdo.setNextProperty(nextProperty);
		return detailRdo;
	}

	/**
	 * 행안부 원본 좌표(entX/entY)는 응답에 싣지 않는다.
	 */
	public PropertyLocationRdo toPropertyLocationRdo(PropertyLocation location) {
		if (location == null) {
			return null;
		}
		return new PropertyLocationRdo(location.getBdMgtSn(), location.getRoadAddress(), location.getLatitude(),
			location.getLongitude());
	}

	public ViewedPropertyBriefRdo toViewedPropertyBriefRdo(ViewedProperty property) {
		if (property == null) {
			return null;
		}
		return new ViewedPropertyBriefRdo(property.getId(), property.getComplexName(), property.getName(),
			property.getInterestLevel());
	}

	/**
	 * 저장 순서에 기대지 않고 읽을 때 분류 순서로 정렬한다(계획 §2) - 스냅샷 배열이 어떤 순서로
	 * 저장돼 있든 응답 순서가 같다.
	 */
	private List<PropertyAnswerRdo> toPropertyAnswerRdos(List<PropertyAnswer> answers,
		Map<String, InspectionQuestion> questions) {
		if (answers == null) {
			return new ArrayList<>();
		}
		return answers.stream()
			.sorted(InspectionQuestionOrdering.answerComparator())
			.map(answer -> propertyAnswerMapper.toPropertyAnswerRdo(answer, questions.get(answer.getQuestionId())))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * 임장 사진과 매물 사진이 한 FileBox에 함께 들어 있어, 두 매퍼가 같은 방식으로 갈라 쓴다.
	 */
	static FileBoxItemRdo coverOf(List<FileBoxItemRdo> files, FileBoxTargetType targetType, String targetId) {
		return files.stream()
			.filter(file -> targetType == file.getTargetType())
			.filter(file -> equalsTargetId(targetId, file.getTargetId()))
			.filter(file -> FileBoxItemRole.COVER == file.getRole())
			.findFirst()
			.orElse(null);
	}

	static List<FileBoxItemRdo> photosOf(List<FileBoxItemRdo> files, FileBoxTargetType targetType, String targetId) {
		return files.stream()
			.filter(file -> targetType == file.getTargetType())
			.filter(file -> equalsTargetId(targetId, file.getTargetId()))
			.filter(file -> FileBoxItemRole.GALLERY == file.getRole())
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private static boolean equalsTargetId(String expected, String actual) {
		if (expected == null) {
			return actual == null;
		}
		return expected.equals(actual);
	}
}
