package com.seoulchonnom.aggregate.flow.inspection;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaInUseException;
import com.seoulchonnom.aggregate.inspection.logic.InspectionAreaLogic;
import com.seoulchonnom.aggregate.inspection.logic.InspectionVisitLogic;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;

import lombok.RequiredArgsConstructor;

/**
 * 지역 커맨드. 삭제만 임장 테이블을 봐야 해서 Flow가 필요하다.
 *
 * 등록/수정 응답의 집계 필드는 0이다. 방금 만든 지역에는 임장이 없고, 수정은 집계를 바꾸지 않는다.
 * 집계가 필요한 화면은 목록·상세를 다시 부른다.
 */
@Service
@RequiredArgsConstructor
public class InspectionAreaFlow {
	private final InspectionAreaLogic inspectionAreaLogic;
	private final InspectionVisitLogic inspectionVisitLogic;
	private final InspectionAreaMapper inspectionAreaMapper;

	@Transactional
	public InspectionAreaRdo registerInspectionArea(InspectionAreaCdo inspectionAreaCdo) {
		return toRdo(inspectionAreaLogic.registerInspectionArea(inspectionAreaCdo));
	}

	@Transactional
	public InspectionAreaRdo modifyInspectionArea(String areaId, InspectionAreaUdo inspectionAreaUdo) {
		return toRdo(inspectionAreaLogic.modifyInspectionArea(areaId, inspectionAreaUdo));
	}

	/**
	 * 임장 기록이 1건이라도 있으면 409. 지역을 지우면 그 기록들이 없는 지역을 가리키게 되어
	 * 상세 조회가 깨진다. 비활성화가 필요하면 hidden을 쓴다.
	 */
	@Transactional
	public void deleteInspectionArea(String areaId) {
		InspectionArea area = inspectionAreaLogic.getInspectionArea(areaId);
		if (inspectionVisitLogic.hasVisitOfArea(areaId)) {
			throw new InspectionAreaInUseException();
		}
		inspectionAreaLogic.deleteInspectionArea(area);
	}

	private InspectionAreaRdo toRdo(InspectionArea area) {
		return inspectionAreaMapper.toInspectionAreaRdo(area, 0, null, null, 0, null, null, null, List.of(), 0);
	}
}
