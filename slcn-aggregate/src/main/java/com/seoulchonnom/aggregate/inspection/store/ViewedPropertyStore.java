package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

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

	public ViewedProperty save(ViewedProperty property) {
		return viewedPropertyJpoMapper.toDomain(
			viewedPropertyRepository.save(viewedPropertyJpoMapper.toJpo(property)));
	}

	public List<ViewedProperty> saveAll(List<ViewedProperty> properties) {
		return viewedPropertyRepository.saveAll(properties.stream()
				.map(viewedPropertyJpoMapper::toJpo)
				.toList()).stream()
			.map(viewedPropertyJpoMapper::toDomain)
			.toList();
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
