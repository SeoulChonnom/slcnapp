package com.seoulchonnom.aggregate.inspection.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaDuplicatedException;
import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaNotFoundException;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionAreaJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionAreaRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InspectionAreaStore {
	private final InspectionAreaRepository inspectionAreaRepository;
	private final InspectionAreaJpoMapper inspectionAreaJpoMapper;

	/**
	 * 지역명 유니크 위반을 409로 바꾼다. 앱 레벨 중복 검사를 통과한 두 요청이 동시에
	 * 들어오는 경우가 여기로 온다. flush를 당겨야 예외가 이 메서드 안에서 잡힌다.
	 */
	public InspectionArea save(InspectionArea area) {
		try {
			return inspectionAreaJpoMapper.toDomain(
				inspectionAreaRepository.saveAndFlush(inspectionAreaJpoMapper.toJpo(area)));
		} catch (DataIntegrityViolationException e) {
			throw new InspectionAreaDuplicatedException();
		}
	}

	public InspectionArea findById(String areaId) {
		return inspectionAreaRepository.findById(areaId)
			.map(inspectionAreaJpoMapper::toDomain)
			.orElseThrow(InspectionAreaNotFoundException::new);
	}

	public Optional<InspectionArea> findOptionalByName(String name) {
		return inspectionAreaRepository.findByName(name).map(inspectionAreaJpoMapper::toDomain);
	}

	public List<InspectionArea> findAllVisible(String keyword) {
		return (StringUtils.hasText(keyword)
			? inspectionAreaRepository.findAllByHiddenFalseAndNameContainingOrderByNameAsc(keyword.trim())
			: inspectionAreaRepository.findAllByHiddenFalseOrderByNameAsc()).stream()
			.map(inspectionAreaJpoMapper::toDomain)
			.toList();
	}

	public List<InspectionArea> findAllByIds(Collection<String> areaIds) {
		if (areaIds == null || areaIds.isEmpty()) {
			return List.of();
		}
		return inspectionAreaRepository.findAllByIdIn(areaIds).stream()
			.map(inspectionAreaJpoMapper::toDomain)
			.toList();
	}

	public void delete(InspectionArea area) {
		inspectionAreaRepository.deleteById(area.getId());
	}
}
