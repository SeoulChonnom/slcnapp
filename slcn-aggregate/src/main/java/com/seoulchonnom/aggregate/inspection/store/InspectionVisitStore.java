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

	public List<InspectionVisit> findAll() {
		return toDomains(inspectionVisitRepository.findAllByOrderByVisitedAtDescIdAsc());
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

	public List<InspectionVisit> findAllBetween(LocalDateTime from, LocalDateTime to) {
		return toDomains(inspectionVisitRepository.findAllByVisitedAtBetweenOrderByVisitedAtDescIdAsc(from, to));
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
