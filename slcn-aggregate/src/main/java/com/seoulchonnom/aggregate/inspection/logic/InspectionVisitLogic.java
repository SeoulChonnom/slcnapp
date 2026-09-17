package com.seoulchonnom.aggregate.inspection.logic;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.exception.InvalidInspectionVisitException;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;

import lombok.RequiredArgsConstructor;

/**
 * 임장 기록 한 건(회차)의 기본 정보를 다룬다.
 *
 * revisitIntent는 요구사항상 필수지만 DRAFT 부분 저장을 허용해야 하므로 저장 제약이 아니라
 * 완료 조건으로 다룬다. visitedAt은 DRAFT에서도 필수다 — 목록 정렬 축이기 때문이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionVisitLogic {
	private static final int MAX_ONE_LINE_REVIEW_LENGTH = 300;
	private static final int MAX_TEXT_LENGTH = 5000;

	private final InspectionVisitStore inspectionVisitStore;

	public InspectionVisit getInspectionVisit(String visitId) {
		return inspectionVisitStore.findById(visitId);
	}

	public List<InspectionVisit> getInspectionVisitsByAreaId(String areaId) {
		return inspectionVisitStore.findAllByAreaId(areaId);
	}

	public boolean hasVisitOfArea(String areaId) {
		return inspectionVisitStore.existsByAreaId(areaId);
	}

	@Transactional
	public InspectionVisit save(InspectionVisit visit) {
		return inspectionVisitStore.save(visit);
	}

	@Transactional
	public void delete(String visitId) {
		inspectionVisitStore.delete(visitId);
	}

	/**
	 * 스칼라 필드는 생략하면 null로 덮어쓴다. PUT은 전체 교체이고,
	 * Jackson POJO가 "필드 없음"과 "명시적 null"을 구별하지 못하기 때문이다.
	 * 컬렉션(tags, files)만 생략 시 유지되며 그 처리는 Flow가 한다.
	 */
	public void applyUpdate(InspectionVisit visit, InspectionVisitUdo inspectionVisitUdo) {
		LocalDateTime visitedAt = parseVisitedAt(inspectionVisitUdo.getVisitedAt());
		validateTexts(inspectionVisitUdo.getOneLineReview(), inspectionVisitUdo.getMemo(),
			inspectionVisitUdo.getPros(), inspectionVisitUdo.getCons());

		visit.update(visitedAt, trimToNull(inspectionVisitUdo.getMemo()), inspectionVisitUdo.getRevisitIntent(),
			trimToNull(inspectionVisitUdo.getOneLineReview()), trimToNull(inspectionVisitUdo.getPros()),
			trimToNull(inspectionVisitUdo.getCons()));
	}

	/**
	 * visitedAt은 DRAFT에서도 필수다.
	 */
	public LocalDateTime parseVisitedAt(String rawVisitedAt) {
		if (!StringUtils.hasText(rawVisitedAt)) {
			throw new InvalidInspectionVisitException("임장 일시는 필수입니다.");
		}
		try {
			return LocalDateTime.parse(rawVisitedAt.trim());
		} catch (DateTimeParseException e) {
			throw new InvalidInspectionVisitException("임장 일시 형식이 올바르지 않습니다. 예: 2026-09-17T14:00:00");
		}
	}

	/**
	 * 요구사항 §36의 완료 조건 중 임장 자신의 필드만 검사한다.
	 * 하위 매물 조건은 Flow가 확인한다 — 이 Logic은 매물 테이블을 모른다.
	 */
	public List<String> findMissingFieldsForCompletion(InspectionVisit visit) {
		List<String> missing = new ArrayList<>();
		if (visit.getVisitedAt() == null) {
			missing.add("visitedAt");
		}
		if (visit.getRevisitIntent() == null) {
			missing.add("revisitIntent");
		}
		return missing;
	}

	public boolean isCompleted(InspectionVisit visit) {
		return InspectionStatus.COMPLETED == visit.getStatus();
	}

	private void validateTexts(String oneLineReview, String memo, String pros, String cons) {
		if (oneLineReview != null && oneLineReview.length() > MAX_ONE_LINE_REVIEW_LENGTH) {
			throw new InvalidInspectionVisitException("한줄평이 너무 깁니다.");
		}
		for (String text : List.of(nullToEmpty(memo), nullToEmpty(pros), nullToEmpty(cons))) {
			if (text.length() > MAX_TEXT_LENGTH) {
				throw new InvalidInspectionVisitException("입력이 너무 깁니다.");
			}
		}
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
