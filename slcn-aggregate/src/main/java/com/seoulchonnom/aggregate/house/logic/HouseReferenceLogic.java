package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.aggregate.house.stat.NearestSchoolCalculator;
import com.seoulchonnom.aggregate.house.stat.NearestSchoolCalculator.Nearest;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseReferenceStore;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseSchool;

import lombok.RequiredArgsConstructor;

/**
 * 참조 데이터(학교) 교체와 단지별 최근접 학교 재생성. 외부 호출은 트랜잭션 밖에서 끝내고, 이 클래스는 받은 값만 저장한다.
 * 학교 목록과 최근접 결과가 어긋나지 않도록 한 트랜잭션에서 둘 다 바꾼다. 실패하면 전부 롤백되어 이전 데이터가 남는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseReferenceLogic {
	private final HouseReferenceStore houseReferenceStore;
	private final HouseComplexStore houseComplexStore;

	/**
	 * house_school을 upsert하고 이번에 없는 학교를 지운 뒤, 최근접 학교를 다시 만든다.
	 * 빈 목록은 교체하지 않고 예외로 거절한다. 수집이 비정상인데 기존 학교를 전부 지우는 일을 막으려는 것이다.
	 */
	@Transactional
	public SchoolSyncResult replaceSchools(List<SchoolRecord> records, LocalDate today) {
		if (records == null || records.isEmpty()) {
			throw new IllegalArgumentException("학교 목록이 비어 있습니다.");
		}
		long now = System.currentTimeMillis();
		for (SchoolRecord record : records) {
			houseReferenceStore.upsertSchool(record, today, now);
		}
		int deleted = houseReferenceStore.deleteSchoolsExcept(records.stream().map(SchoolRecord::schoolCode).toList());
		SchoolSyncResult nearest = rebuildNearest();
		return new SchoolSyncResult(nearest.schools(), deleted, nearest.complexes(), nearest.nearestRows());
	}

	/**
	 * 학교는 그대로 두고 최근접 학교만 다시 만든다. 좌표가 새로 생긴 단지가 다음 학교 동기화(최대 180일)를
	 * 기다리지 않게 하려는 것이다.
	 */
	@Transactional
	public SchoolSyncResult recomputeNearest() {
		return rebuildNearest();
	}

	private SchoolSyncResult rebuildNearest() {
		List<ComplexPointPdo> complexes = houseComplexStore.findGeocodedPoints();
		List<HouseSchool> schools = houseReferenceStore.findAllSchools();
		List<Nearest> nearest = NearestSchoolCalculator.calculate(complexes, schools);
		int rows = houseReferenceStore.replaceComplexSchools(nearest);
		return new SchoolSyncResult(schools.size(), 0, complexes.size(), rows);
	}
}
