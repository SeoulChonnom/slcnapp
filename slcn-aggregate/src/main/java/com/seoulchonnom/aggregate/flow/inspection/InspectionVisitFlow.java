package com.seoulchonnom.aggregate.flow.inspection;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.common.transaction.AfterCommitExecutor;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.logic.ViewedPropertyLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;

import lombok.RequiredArgsConstructor;

/**
 * 임장 등록 한 번이 지역 조회/생성 + 임장 저장 + 태그 upsert·연결 + FileBox 동기화를 묶는다.
 * 이런 복합 커맨드는 Logic이 아니라 Flow가 맡는다.
 *
 * FileBox는 MongoDB라 PostgreSQL 트랜잭션에 참여하지 않는다. 등록·수정은 RDB 저장이 끝난 뒤에
 * 동기화한다 — 반대로 하면 RDB 롤백 시 고아 FileBox가 남는다.
 */
@Service
@RequiredArgsConstructor
public class InspectionVisitFlow {
	private final InspectionVisitLogic inspectionVisitLogic;
	private final InspectionAreaLogic inspectionAreaLogic;
	private final InspectionTagLogic inspectionTagLogic;
	private final InspectionTagStore inspectionTagStore;
	private final InspectionPhotoSupport inspectionPhotoSupport;
	private final ViewedPropertyLogic viewedPropertyLogic;
	private final IdGenerator idGenerator;

	/**
	 * areaId 또는 인라인 area 중 하나가 필수다. 임장은 항상 DRAFT로 생성된다.
	 */
	@Transactional
	public InspectionVisit registerInspectionVisit(InspectionVisitCdo inspectionVisitCdo) {
		InspectionArea area = resolveArea(inspectionVisitCdo);
		String visitId = idGenerator.nextDomainId(SequenceName.INSPECTION_VISIT.toString());

		InspectionVisit visit = new InspectionVisit(visitId, area.getId(),
			inspectionVisitLogic.parseVisitedAt(inspectionVisitCdo.getVisitedAt()));
		inspectionVisitLogic.applyUpdate(visit, toUdo(inspectionVisitCdo));

		InspectionVisit saved = inspectionVisitLogic.save(visit);
		linkTags(saved.getId(), inspectionVisitCdo.getTags());
		inspectionPhotoSupport.syncVisitPhotos(saved.getId(), toFileUdos(inspectionVisitCdo));
		return saved;
	}

	/**
	 * 스칼라는 전체 교체, 컬렉션(tags/files)은 생략 시 유지다.
	 * travel의 tags는 반대로 동작하니(normalizeTags(null)이 빈 목록을 반환) 그 코드를 옮겨오지 않는다.
	 */
	@Transactional
	public InspectionVisit modifyInspectionVisit(String visitId, InspectionVisitUdo inspectionVisitUdo) {
		InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);
		inspectionVisitLogic.applyUpdate(visit, inspectionVisitUdo);
		// 스칼라 전체 교체라 revisitIntent를 생략한 요청이 COMPLETED 임장의 완료 조건을 깬다.
		// 매물 쪽 revalidateIfCompleted와 같은 이유로 여기서도 막는다.
		inspectionVisitLogic.revalidateIfCompleted(visit);

		InspectionVisit saved = inspectionVisitLogic.save(visit);
		if (inspectionVisitUdo.getTags() != null) {
			linkTags(saved.getId(), inspectionVisitUdo.getTags());
		}
		inspectionPhotoSupport.syncVisitPhotos(saved.getId(), inspectionVisitUdo.getFiles());
		return saved;
	}

	/**
	 * 요구사항 §36의 임장 완료 조건.
	 *
	 * 매물 0건 임장도 완료할 수 있다 — "성수동 상권만 확인하고 돌아온 임장"이 정상 기록이다.
	 * 4번 조건은 "매물이 있다면 전부 완료"로 읽는다.
	 */
	@Transactional
	public InspectionVisit changeInspectionVisitStatus(String visitId, InspectionStatus status) {
		InspectionVisit visit = inspectionVisitLogic.getInspectionVisit(visitId);
		if (status == null) {
			throw new InvalidInspectionVisitException("상태 값은 필수입니다.");
		}
		if (InspectionStatus.COMPLETED == status) {
			validateCompletable(visit);
		}
		visit.changeStatus(status);
		return inspectionVisitLogic.save(visit);
	}

	private void validateCompletable(InspectionVisit visit) {
		List<String> missing = inspectionVisitLogic.findMissingFieldsForCompletion(visit);
		if (!missing.isEmpty()) {
			throw new InvalidInspectionVisitException("임장 완료 조건을 만족하지 않습니다. missingFields=" + missing);
		}
		List<String> draftPropertyIds = viewedPropertyLogic.getViewedProperties(visit.getId()).stream()
			.filter(property -> InspectionStatus.COMPLETED != property.getStatus())
			.map(ViewedProperty::getId)
			.toList();
		if (!draftPropertyIds.isEmpty()) {
			throw new InvalidInspectionVisitException(
				"필수 조건을 만족하지 않은 매물이 있습니다. propertyIds=" + draftPropertyIds);
		}
	}

	/**
	 * RDB를 먼저 커밋하고 FileBox를 나중에 지운다.
	 *
	 * 순서를 뒤집으면 RDB 커밋이 실패했을 때 fileAssetId 목록 자체를 잃어 사진을 복구할 수 없다.
	 * 이 방향에서 남는 것은 아무도 참조하지 않는 고아 FileBox 문서 하나뿐이고 정리 배치로 지운다.
	 *
	 * Mongo 삭제를 메서드 본문에 두면 트랜잭션 프록시가 commit을 호출하기 전에 실행되어
	 * 순서가 반대가 된다. 그래서 afterCommit으로 미룬다.
	 */
	@Transactional
	public void deleteInspectionVisit(String visitId) {
		inspectionVisitLogic.getInspectionVisit(visitId);
		inspectionTagStore.deletePropertyLinksByVisitId(visitId);
		viewedPropertyLogic.deleteByVisitId(visitId);
		inspectionTagStore.deleteVisitLinks(visitId);
		inspectionVisitLogic.delete(visitId);

		AfterCommitExecutor.run(() -> inspectionPhotoSupport.deleteAll(visitId));
	}

	/**
	 * 사진 정렬 일괄 갱신. itemId가 FileBox 문서 안에서 유일하므로
	 * 임장 사진과 매물 사진을 한 경로가 함께 처리한다.
	 */
	@Transactional
	public void modifyInspectionImageOrder(String visitId, List<FileBoxItemOrderUdo> orders) {
		inspectionVisitLogic.getInspectionVisit(visitId);
		inspectionPhotoSupport.applyItemOrder(visitId, orders);
	}

	private InspectionArea resolveArea(InspectionVisitCdo inspectionVisitCdo) {
		if (StringUtils.hasText(inspectionVisitCdo.getAreaId())) {
			return inspectionAreaLogic.getInspectionArea(inspectionVisitCdo.getAreaId());
		}
		if (inspectionVisitCdo.getArea() == null) {
			throw new InvalidInspectionVisitException("임장 지역은 필수입니다. areaId 또는 area 중 하나를 보내야 합니다.");
		}
		return inspectionAreaLogic.registerInspectionArea(inspectionVisitCdo.getArea());
	}

	private void linkTags(String visitId, List<String> tagNames) {
		List<InspectionTag> tags = inspectionTagLogic.resolveTags(tagNames);
		inspectionTagStore.linkVisit(visitId, tags);
	}

	private List<FileBoxItemUdo> toFileUdos(InspectionVisitCdo inspectionVisitCdo) {
		if (inspectionVisitCdo.getFiles() == null) {
			return null;
		}
		return inspectionVisitCdo.getFiles().stream()
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

	private InspectionVisitUdo toUdo(InspectionVisitCdo inspectionVisitCdo) {
		InspectionVisitUdo udo = new InspectionVisitUdo();
		udo.setVisitedAt(inspectionVisitCdo.getVisitedAt());
		udo.setMemo(inspectionVisitCdo.getMemo());
		udo.setRevisitIntent(inspectionVisitCdo.getRevisitIntent());
		udo.setOneLineReview(inspectionVisitCdo.getOneLineReview());
		udo.setPros(inspectionVisitCdo.getPros());
		udo.setCons(inspectionVisitCdo.getCons());
		return udo;
	}
}
