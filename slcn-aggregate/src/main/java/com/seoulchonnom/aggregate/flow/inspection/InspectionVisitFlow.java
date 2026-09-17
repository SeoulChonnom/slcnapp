package com.seoulchonnom.aggregate.flow.inspection;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionTagLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
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

		InspectionVisit saved = inspectionVisitLogic.save(visit);
		if (inspectionVisitUdo.getTags() != null) {
			linkTags(saved.getId(), inspectionVisitUdo.getTags());
		}
		inspectionPhotoSupport.syncVisitPhotos(saved.getId(), inspectionVisitUdo.getFiles());
		return saved;
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
