package com.seoulchonnom.aggregate.flow.inspection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.transaction.AfterCommitExecutor;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionOrderException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidViewedPropertyException;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.ComplexNameScope;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerBulkUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyUdo;

import lombok.RequiredArgsConstructor;

/**
 * 매물 등록 한 번이 매물 저장 + 활성 질문 스냅샷 생성 + 태그 + FileBox를 묶는다.
 *
 * 상태 전이의 자동 복귀 규칙이 여기에 있다. COMPLETED 임장에 DRAFT 매물이 생기면
 * 임장 완료 조건이 깨지므로 임장을 DRAFT로 되돌린다. 409를 던지면 사용자가
 * "매물을 추가하려면 먼저 임장을 미완료로 바꾸세요"라는 무의미한 단계를 밟아야 한다.
 */
@Service
@RequiredArgsConstructor
public class ViewedPropertyFlow {
	private final ViewedPropertyLogic viewedPropertyLogic;
	private final InspectionVisitLogic inspectionVisitLogic;
	private final InspectionQuestionLogic inspectionQuestionLogic;
	private final InspectionTagLogic inspectionTagLogic;
	private final InspectionTagStore inspectionTagStore;
	private final InspectionPhotoSupport inspectionPhotoSupport;

	@Transactional
	public ViewedProperty registerViewedProperty(String visitId, ViewedPropertyCdo viewedPropertyCdo) {
		InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);

		ViewedProperty property = new ViewedProperty(visitId, null, null, nextSortOrder(visitId));
		viewedPropertyLogic.applyUpdate(property, toUdo(viewedPropertyCdo));
		viewedPropertyLogic.materializeAnswers(property, inspectionQuestionLogic.getEnabledQuestions());

		ViewedProperty saved = viewedPropertyLogic.save(property);
		linkTags(saved, viewedPropertyCdo.getTags());
		inspectionPhotoSupport.syncPropertyPhotos(visitId, saved.getId(), toFileUdos(viewedPropertyCdo));

		revertVisitToDraft(visit);
		return saved;
	}

	@Transactional
	public ViewedProperty modifyViewedProperty(String visitId, String propertyId,
		ViewedPropertyUdo viewedPropertyUdo) {
		ViewedProperty property = findOwnedProperty(visitId, propertyId);
		viewedPropertyLogic.applyUpdate(property, viewedPropertyUdo);
		// COMPLETED 매물이면 저장 전에 조건을 다시 본다. 위반하면 아무것도 저장하지 않는다
		viewedPropertyLogic.revalidateIfCompleted(property);

		ViewedProperty saved = viewedPropertyLogic.save(property);
		if (viewedPropertyUdo.getTags() != null) {
			linkTags(saved, viewedPropertyUdo.getTags());
		}
		inspectionPhotoSupport.syncPropertyPhotos(visitId, propertyId, viewedPropertyUdo.getFiles());
		return saved;
	}

	/**
	 * 문답 저장도 기본 정보 수정과 같은 재검증을 거친다. 필수 문답 조건은 오직 답변 값으로만
	 * 깨지는데, 이 경로에 재검증이 없으면 필수 문항이 빈 COMPLETED 매물이 영구히 남는다.
	 */
	@Transactional
	public ViewedProperty modifyPropertyAnswers(String visitId, String propertyId,
		PropertyAnswerBulkUdo propertyAnswerBulkUdo) {
		ViewedProperty property = findOwnedProperty(visitId, propertyId);
		viewedPropertyLogic.applyAnswers(property, propertyAnswerBulkUdo.getAnswers());
		viewedPropertyLogic.revalidateIfCompleted(property);
		return viewedPropertyLogic.save(property);
	}

	@Transactional
	public ViewedProperty changeViewedPropertyStatus(String visitId, String propertyId, InspectionStatus status) {
		ViewedProperty property = findOwnedProperty(visitId, propertyId);
		if (status == null) {
			throw new InvalidViewedPropertyException("상태 값은 필수입니다.");
		}
		if (InspectionStatus.COMPLETED == status) {
			viewedPropertyLogic.validateCompletable(property);
		}
		property.changeStatus(status);
		ViewedProperty saved = viewedPropertyLogic.save(property);

		if (InspectionStatus.DRAFT == status) {
			revertVisitToDraft(inspectionVisitLogic.getInspectionVisit(visitId));
		}
		return saved;
	}

	@Transactional
	public void deleteViewedProperty(String visitId, String propertyId) {
		findOwnedProperty(visitId, propertyId);
		inspectionTagStore.deletePropertyLinks(propertyId);
		viewedPropertyLogic.deleteViewedProperty(propertyId);
		// RDB 커밋이 끝난 뒤에 사진 연결을 끊는다. 본문에서 바로 지우면 커밋보다 먼저 확정되어
		// 커밋 실패 시 매물은 남고 사진만 사라진다
		AfterCommitExecutor.run(() -> inspectionPhotoSupport.removePropertyPhotos(visitId, propertyId));
	}

	/**
	 * 요청에 빠진 매물은 기존 순서를 유지한다. 정렬은 상태 전이를 유발하지 않으므로
	 * COMPLETED 임장에서도 허용한다.
	 */
	@Transactional
	public void modifyViewedPropertyOrder(String visitId, List<ViewedPropertyOrderUdo> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		Map<String, Integer> requested = new HashMap<>();
		for (ViewedPropertyOrderUdo order : orders) {
			if (!StringUtils.hasText(order.getPropertyId())) {
				throw new InvalidInspectionOrderException("정렬 대상 매물 ID가 비어 있습니다.");
			}
			requested.put(order.getPropertyId(), order.getSortOrder());
		}

		List<ViewedProperty> properties = viewedPropertyLogic.getViewedProperties(visitId).stream()
			.filter(property -> requested.containsKey(property.getId()))
			.toList();
		if (properties.size() != requested.size()) {
			throw new InvalidInspectionOrderException("이 임장에 속하지 않은 매물이 정렬 요청에 포함되었습니다.");
		}
		properties.forEach(property -> property.changeSortOrder(requested.get(property.getId())));
		viewedPropertyLogic.saveAll(properties);
	}

	/**
	 * 단지명 자동완성 후보. scope=VISIT(기본)은 이 임장에서 이미 쓴 이름만, scope=AREA는
	 * 이 임장이 속한 지역의 모든 회차에서 쓴 이름을 준다. 첫 회차라 이 임장에 이름이 하나도
	 * 없을 때 과거 회차의 이름이야말로 "같은 이름이어야 회차 간 매물이 연결됩니다"의 후보다.
	 */
	public List<String> getComplexNames(String visitId, ComplexNameScope scope) {
		if (ComplexNameScope.AREA == scope) {
			InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);
			List<String> areaVisitIds = inspectionVisitLogic.getInspectionVisitsByAreaId(visit.getAreaId()).stream()
				.map(InspectionVisit::getId)
				.toList();
			return viewedPropertyLogic.getDistinctComplexNames(areaVisitIds);
		}
		return viewedPropertyLogic.getDistinctComplexNames(List.of(visitId));
	}

	private ViewedProperty findOwnedProperty(String visitId, String propertyId) {
		ViewedProperty property = viewedPropertyLogic.getViewedProperty(propertyId);
		if (!visitId.equals(property.getInspectionVisitId())) {
			throw new ViewedPropertyNotFoundException("이 임장에 속한 매물이 아닙니다. propertyId=" + propertyId);
		}
		return property;
	}

	private int nextSortOrder(String visitId) {
		return viewedPropertyLogic.getViewedProperties(visitId).stream()
			.mapToInt(ViewedProperty::getSortOrder)
			.max()
			.orElse(0) + 1;
	}

	/**
	 * 상태는 검증 결과의 표현이므로 조건이 깨지면 표현이 따라간다.
	 */
	private void revertVisitToDraft(InspectionVisit visit) {
		if (InspectionStatus.COMPLETED != visit.getStatus()) {
			return;
		}
		visit.changeStatus(InspectionStatus.DRAFT);
		inspectionVisitLogic.save(visit);
	}

	private void linkTags(ViewedProperty property, List<String> tagNames) {
		List<InspectionTag> tags = inspectionTagLogic.resolveTags(tagNames);
		inspectionTagStore.linkProperty(property.getId(), property.getInspectionVisitId(), tags);
	}

	private List<FileBoxItemUdo> toFileUdos(ViewedPropertyCdo viewedPropertyCdo) {
		if (viewedPropertyCdo.getFiles() == null) {
			return null;
		}
		return viewedPropertyCdo.getFiles().stream()
			.map(cdo -> {
				FileBoxItemUdo udo = new FileBoxItemUdo();
				udo.setFileAssetId(cdo.getFileAssetId());
				udo.setRole(cdo.getRole());
				udo.setCaption(cdo.getCaption());
				udo.setSortOrder(cdo.getSortOrder());
				return udo;
			})
			.toList();
	}

	private ViewedPropertyUdo toUdo(ViewedPropertyCdo viewedPropertyCdo) {
		ViewedPropertyUdo udo = new ViewedPropertyUdo();
		udo.setComplexName(viewedPropertyCdo.getComplexName());
		udo.setName(viewedPropertyCdo.getName());
		udo.setMemo(viewedPropertyCdo.getMemo());
		udo.setOneLineReview(viewedPropertyCdo.getOneLineReview());
		udo.setPros(viewedPropertyCdo.getPros());
		udo.setCons(viewedPropertyCdo.getCons());
		udo.setInterestLevel(viewedPropertyCdo.getInterestLevel());
		return udo;
	}
}
