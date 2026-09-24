package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionTagScope;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;

/**
 * 생성/삭제 API를 두지 않는다. 임장/매물 저장 시 이름 기반 get-or-create로 처리한다.
 */
public interface InspectionTagFacade {
	/**
	 * @param scope null이면 임장/매물 사용 빈도를 합산한다(하위호환). VISIT/PROPERTY면 해당 용도의
	 *              사용 빈도만 usageCount에 반영한다(B-⑨).
	 */
	ResponseEntity<List<InspectionTagRdo>> getInspectionTags(String keyword, InspectionTagScope scope);
}
