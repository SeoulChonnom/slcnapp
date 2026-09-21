package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyConflictException;
import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.mapper.ViewedPropertyJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.projection.ViewedPropertySummaryPdo;
import com.seoulchonnom.aggregate.inspection.store.repository.ViewedPropertyRepository;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ViewedPropertyStore {
	private final ViewedPropertyRepository viewedPropertyRepository;
	private final ViewedPropertyJpoMapper viewedPropertyJpoMapper;

	/**
	 * EntityJpo의 @Version이 모든 엔티티에 낙관적 잠금을 건다. 두 사용자가 같은 매물을
	 * 동시에 저장하면 이 충돌이 난다. 커밋 시점까지 미루면 예외가 트랜잭션 밖에서 500으로
	 * 새어나가므로 saveAndFlush로 당겨 이 메서드 안에서 409로 바꾼다.
	 */
	public ViewedProperty save(ViewedProperty property) {
		try {
			return viewedPropertyJpoMapper.toDomain(
				viewedPropertyRepository.saveAndFlush(viewedPropertyJpoMapper.toJpo(property)));
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new ViewedPropertyConflictException();
		}
	}

	public List<ViewedProperty> saveAll(List<ViewedProperty> properties) {
		try {
			return viewedPropertyRepository.saveAllAndFlush(properties.stream()
					.map(viewedPropertyJpoMapper::toJpo)
					.toList()).stream()
				.map(viewedPropertyJpoMapper::toDomain)
				.toList();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new ViewedPropertyConflictException();
		}
	}

	public ViewedProperty findById(String propertyId) {
		return viewedPropertyRepository.findById(propertyId)
			.map(viewedPropertyJpoMapper::toDomain)
			.orElseThrow(ViewedPropertyNotFoundException::new);
	}

	/**
	 * 상세 화면용. answers가 함께 온다.
	 */
	public List<ViewedProperty> findAllByVisitId(String inspectionVisitId) {
		return viewedPropertyRepository
			.findAllByInspectionVisitIdOrderBySortOrderAscRegisteredTimeAscIdAsc(inspectionVisitId).stream()
			.map(viewedPropertyJpoMapper::toDomain)
			.toList();
	}

	public List<ViewedProperty> findAllByIds(Collection<String> propertyIds) {
		if (propertyIds == null || propertyIds.isEmpty()) {
			return List.of();
		}
		return viewedPropertyRepository.findAllByIdIn(propertyIds).stream()
			.map(viewedPropertyJpoMapper::toDomain)
			.toList();
	}

	/**
	 * 목록·집계용. answers를 읽지 않는다.
	 */
	public List<ViewedPropertySummaryPdo> findSummariesByVisitIds(Collection<String> inspectionVisitIds) {
		if (inspectionVisitIds == null || inspectionVisitIds.isEmpty()) {
			return List.of();
		}
		return viewedPropertyRepository.findAllByInspectionVisitIdIn(inspectionVisitIds);
	}

	/**
	 * 질문별 답변 수 집계 전용 전건 조회. answers가 전부 따라오므로 관리자 화면에서만 쓴다.
	 */
	public List<ViewedProperty> findAll() {
		return viewedPropertyRepository.findAll().stream()
			.map(viewedPropertyJpoMapper::toDomain)
			.toList();
	}

	public long countByVisitId(String inspectionVisitId) {
		return viewedPropertyRepository.countByInspectionVisitId(inspectionVisitId);
	}

	public void delete(String propertyId) {
		viewedPropertyRepository.deleteById(propertyId);
	}

	public void deleteByVisitId(String inspectionVisitId) {
		viewedPropertyRepository.deleteByInspectionVisitId(inspectionVisitId);
	}
}
