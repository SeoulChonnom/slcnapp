package com.seoulchonnom.aggregate.inspection.logic;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.inspection.exception.InspectionAreaDuplicatedException;
import com.seoulchonnom.aggregate.inspection.store.InspectionAreaStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.inspection.entity.InspectionArea;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionAreaMapper;

import lombok.RequiredArgsConstructor;

/**
 * 임장을 다니는 지역/생활권. 개별 단지가 아니다 — 단지명은 매물의 complexName으로 내려간다.
 *
 * visitCount 같은 집계는 저장하지 않는다. 임장 등록/삭제와 동기화가 어긋날 여지를 만들지 않기 위해서다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InspectionAreaLogic {
	private static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_DESCRIPTION_LENGTH = 300;

	private final InspectionAreaStore inspectionAreaStore;
	private final InspectionAreaMapper inspectionAreaMapper;
	private final IdGenerator idGenerator;

	public List<InspectionArea> getInspectionAreas(String keyword) {
		return inspectionAreaStore.findAllVisible(keyword);
	}

	public InspectionArea getInspectionArea(String areaId) {
		return inspectionAreaStore.findById(areaId);
	}

	/**
	 * 지역명이 겹치면 만들지 않고 409로 막는다. 중복 생성되면 같은 생활권의 재임장 이력이
	 * 두 갈래로 갈라진다. 동시 요청은 유니크 제약이 막고 Store가 같은 예외로 바꾼다.
	 */
	@Transactional
	public InspectionArea registerInspectionArea(InspectionAreaCdo inspectionAreaCdo) {
		String name = normalizeName(inspectionAreaCdo.getName());
		validateDescription(inspectionAreaCdo.getDescription());
		inspectionAreaStore.findOptionalByName(name).ifPresent(existing -> {
			throw new InspectionAreaDuplicatedException("같은 이름의 임장 지역이 이미 있습니다. areaId=" + existing.getId());
		});

		InspectionAreaCdo normalized = new InspectionAreaCdo(name, trimToNull(inspectionAreaCdo.getDescription()));
		String areaId = idGenerator.nextDomainId(SequenceName.INSPECTION_AREA.toString());
		return inspectionAreaStore.save(inspectionAreaMapper.toInspectionArea(areaId, normalized));
	}

	/**
	 * 지역 정보를 고쳐도 기존 임장 기록은 영향을 받지 않는다. 임장은 areaId만 참조한다.
	 */
	@Transactional
	public InspectionArea modifyInspectionArea(String areaId, InspectionAreaUdo inspectionAreaUdo) {
		InspectionArea area = inspectionAreaStore.findById(areaId);
		String name = normalizeName(inspectionAreaUdo.getName());
		validateDescription(inspectionAreaUdo.getDescription());

		Optional<InspectionArea> sameName = inspectionAreaStore.findOptionalByName(name);
		if (sameName.isPresent() && !sameName.get().getId().equals(areaId)) {
			throw new InspectionAreaDuplicatedException(
				"같은 이름의 임장 지역이 이미 있습니다. areaId=" + sameName.get().getId());
		}

		area.update(name, trimToNull(inspectionAreaUdo.getDescription()));
		return inspectionAreaStore.save(area);
	}

	/**
	 * 임장 기록이 남아 있는지는 호출자(Flow)가 확인한다. 이 Logic은 지역 테이블만 안다.
	 */
	@Transactional
	public void deleteInspectionArea(InspectionArea area) {
		inspectionAreaStore.delete(area);
	}

	private String normalizeName(String rawName) {
		if (!StringUtils.hasText(rawName)) {
			throw new BadRequestException("지역명은 필수입니다.");
		}
		String name = rawName.trim().replaceAll("\\s+", " ");
		if (name.length() > MAX_NAME_LENGTH) {
			throw new BadRequestException("지역명이 너무 깁니다.");
		}
		return name;
	}

	private void validateDescription(String description) {
		if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new BadRequestException("지역 설명이 너무 깁니다.");
		}
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}
}
