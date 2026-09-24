package com.seoulchonnom.aggregate.inspection.store;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitConflictException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionVisitJpo;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionVisitJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionVisitRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InspectionVisitStore {
	private final InspectionVisitRepository inspectionVisitRepository;
	private final InspectionVisitJpoMapper inspectionVisitJpoMapper;

	/**
	 * EntityJpo의 @Version이 모든 엔티티에 낙관적 잠금을 건다. 두 사용자가 같은 임장을
	 * 동시에 저장하면 이 충돌이 난다. 커밋 시점까지 미루면 예외가 트랜잭션 밖에서 500으로
	 * 새어나가므로 saveAndFlush로 당겨 이 메서드 안에서 409로 바꾼다.
	 */
	public InspectionVisit save(InspectionVisit visit) {
		try {
			return inspectionVisitJpoMapper.toDomain(
				inspectionVisitRepository.saveAndFlush(inspectionVisitJpoMapper.toJpo(visit)));
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new InspectionVisitConflictException();
		}
	}

	public InspectionVisit findById(String visitId) {
		return inspectionVisitRepository.findById(visitId)
			.map(inspectionVisitJpoMapper::toDomain)
			.orElseThrow(InspectionVisitNotFoundException::new);
	}

	public List<InspectionVisit> findAllByAreaId(String areaId) {
		return toDomains(inspectionVisitRepository.findAllByAreaIdOrderByVisitedAtDescIdAsc(areaId));
	}

	public List<InspectionVisit> findAllByAreaIds(Collection<String> areaIds) {
		if (areaIds == null || areaIds.isEmpty()) {
			return List.of();
		}
		return toDomains(inspectionVisitRepository.findAllByAreaIdIn(areaIds));
	}

	public List<InspectionVisit> findAllByIds(Collection<String> visitIds) {
		if (visitIds == null || visitIds.isEmpty()) {
			return List.of();
		}
		return toDomains(inspectionVisitRepository.findAllByIdIn(visitIds));
	}

	/**
	 * 임장 목록(C-2)의 필터·페이징을 전부 DB에서 처리한다. status/revisitIntent는 문자열로
	 * 바꿔 네이티브 쿼리에 넘긴다(컬럼이 EnumType.STRING이라 텍스트 비교로 충분하다).
	 */
	public List<InspectionVisit> findFiltered(String areaId, InspectionStatus status, RevisitIntent revisitIntent,
		LocalDateTime from, LocalDateTime to, List<String> tags, int limit, int offset) {
		List<String> tagNames = normalizeTagNames(tags);
		int tagCount = tags == null ? 0 : tags.size();
		return toDomains(inspectionVisitRepository.findFiltered(areaId, status == null ? null : status.name(),
			revisitIntent == null ? null : revisitIntent.name(), from, to, tagNames, tagCount, limit, offset));
	}

	/**
	 * findFiltered와 동일 필터의 매칭 총 건수. 페이지 응답의 totalCount/hasNext 계산용이다.
	 */
	public long countFiltered(String areaId, InspectionStatus status, RevisitIntent revisitIntent,
		LocalDateTime from, LocalDateTime to, List<String> tags) {
		List<String> tagNames = normalizeTagNames(tags);
		int tagCount = tags == null ? 0 : tags.size();
		return inspectionVisitRepository.countFiltered(areaId, status == null ? null : status.name(),
			revisitIntent == null ? null : revisitIntent.name(), from, to, tagNames, tagCount);
	}

	/**
	 * 태그가 없으면 HAVING 절의 tagCount=0 분기가 검사를 건너뛰지만, 네이티브 쿼리의
	 * "t.name IN (:tagNames)"에 빈 컬렉션을 그대로 넘기면 "IN ()"이 되어 SQL 문법 오류가 난다.
	 * 실제 태그명으로는 절대 쓰이지 않을 자리표시 값 하나로 채운다 — tagCount=0이라
	 * HAVING이 이 값을 검사하지 않으므로 결과에 영향은 없다.
	 *
	 * 널 문자("\u0000")를 쓰면 안 된다. PostgreSQL이 텍스트 파라미터의 0x00을 거부해
	 * ("invalid byte sequence for encoding UTF8") 태그 필터 없는 호출이 전부 500이 된다.
	 * 태그명은 InspectionTagLogic이 공백을 제거하고 50자로 제한하므로 아래 값과 겹칠 수 없다.
	 */
	private static final String TAG_FILTER_PLACEHOLDER = " (no tag filter) ";

	private List<String> normalizeTagNames(List<String> tags) {
		return tags == null || tags.isEmpty() ? List.of(TAG_FILTER_PLACEHOLDER) : tags;
	}

	/**
	 * 지역 삭제 전 확인용. 1건이라도 있으면 삭제를 막는다.
	 */
	public boolean existsByAreaId(String areaId) {
		return inspectionVisitRepository.existsByAreaId(areaId);
	}

	public void delete(String visitId) {
		inspectionVisitRepository.deleteById(visitId);
	}

	private List<InspectionVisit> toDomains(List<InspectionVisitJpo> jpos) {
		return jpos.stream().map(inspectionVisitJpoMapper::toDomain).toList();
	}
}
