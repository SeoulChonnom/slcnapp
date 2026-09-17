package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;

/**
 * 생성/삭제 API를 두지 않는다. 임장/매물 저장 시 이름 기반 get-or-create로 처리한다.
 */
public interface InspectionTagFacade {
	ResponseEntity<List<InspectionTagRdo>> getInspectionTags(String keyword);
}
