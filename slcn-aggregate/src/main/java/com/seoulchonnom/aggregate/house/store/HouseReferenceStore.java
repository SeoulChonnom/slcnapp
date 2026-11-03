package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.collect.SchoolRecord;
import com.seoulchonnom.aggregate.house.stat.NearestSchoolCalculator.Nearest;
import com.seoulchonnom.aggregate.house.store.mapper.HouseSchoolJpoMapper;
import com.seoulchonnom.aggregate.house.store.projection.ComplexRedevelopmentPdo;
import com.seoulchonnom.aggregate.house.store.projection.ComplexSchoolPdo;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexRedevelopmentRepository;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexSchoolRepository;
import com.seoulchonnom.aggregate.house.store.repository.HouseSchoolRepository;
import com.seoulchonnom.spec.house.entity.HouseSchool;
import com.seoulchonnom.spec.house.entity.vo.SchoolLevel;

import lombok.RequiredArgsConstructor;

/**
 * 참조 데이터(학교·정비사업)와 단지별 파생 결과. 교체는 호출하는 쪽(HouseReferenceLogic)의 트랜잭션 안에서 한다.
 */
@Repository
@RequiredArgsConstructor
public class HouseReferenceStore {
	/** 한 문장에 싣는 최근접 행 수. 문자열 4개가 너무 커지지 않게 나눈다. */
	private static final int INSERT_CHUNK = 2000;

	private final HouseSchoolRepository houseSchoolRepository;
	private final HouseComplexSchoolRepository houseComplexSchoolRepository;
	private final HouseComplexRedevelopmentRepository houseComplexRedevelopmentRepository;
	private final HouseSchoolJpoMapper houseSchoolJpoMapper;

	/**
	 * school_code가 같은 학교는 값을 덮어쓰고, 없으면 새 UUID로 넣는다.
	 */
	public void upsertSchool(SchoolRecord record, LocalDate syncedDate, long now) {
		houseSchoolRepository.upsert(UUID.randomUUID().toString(), now, record.schoolCode(), record.name(),
			record.level().name(), record.latitude(), record.longitude(), syncedDate);
	}

	/**
	 * 이번 목록에 없는 학교를 지운다. 코드가 비면 전부 지워지는 쿼리가 되므로 실행하지 않는다.
	 *
	 * @return 지운 학교 수
	 */
	public int deleteSchoolsExcept(Collection<String> schoolCodes) {
		if (schoolCodes == null || schoolCodes.isEmpty()) {
			return 0;
		}
		return houseSchoolRepository.deleteExcept(String.join(",", schoolCodes));
	}

	public List<HouseSchool> findAllSchools() {
		return houseSchoolRepository.findAll().stream().map(houseSchoolJpoMapper::toDomain).toList();
	}

	/**
	 * 단지별 최근접 학교를 통째로 바꾼다(전부 지우고 다시 넣는다).
	 *
	 * @return 넣은 행 수
	 */
	public int replaceComplexSchools(List<Nearest> nearest) {
		houseComplexSchoolRepository.deleteAllRows();
		int inserted = 0;
		for (int from = 0; from < nearest.size(); from += INSERT_CHUNK) {
			List<Nearest> chunk = nearest.subList(from, Math.min(from + INSERT_CHUNK, nearest.size()));
			inserted += houseComplexSchoolRepository.insertAll(
				join(chunk, Nearest::complexId),
				join(chunk, row -> row.level().name()),
				join(chunk, Nearest::schoolId),
				join(chunk, row -> String.valueOf(row.distanceM())));
		}
		return inserted;
	}

	/**
	 * 단지들의 학교급별 최근접 학교. 단지, 학교급(초·중·고) 순이다.
	 */
	public List<ComplexSchoolPdo> findComplexSchools(Collection<String> complexIds) {
		if (complexIds == null || complexIds.isEmpty()) {
			return List.of();
		}
		List<ComplexSchoolPdo> rows = new ArrayList<>();
		for (Object[] row : houseComplexSchoolRepository.findByComplexIds(String.join(",", complexIds))) {
			rows.add(new ComplexSchoolPdo((String)row[0], (String)row[1], (String)row[2],
				((Number)row[3]).intValue()));
		}
		rows.sort(Comparator.comparing(ComplexSchoolPdo::complexId)
			.thenComparingInt(row -> SchoolLevel.valueOf(row.level()).ordinal()));
		return rows;
	}

	/**
	 * 단지들의 반경 안 정비사업. 단지, 가까운 순, 이름 순이다.
	 */
	public List<ComplexRedevelopmentPdo> findComplexRedevelopments(Collection<String> complexIds) {
		if (complexIds == null || complexIds.isEmpty()) {
			return List.of();
		}
		List<ComplexRedevelopmentPdo> rows = new ArrayList<>();
		for (Object[] row : houseComplexRedevelopmentRepository.findByComplexIds(String.join(",", complexIds))) {
			rows.add(new ComplexRedevelopmentPdo((String)row[0], (String)row[1], (String)row[2], (String)row[3],
				((Number)row[4]).intValue(), (String)row[5]));
		}
		return rows;
	}

	private static <T> String join(List<T> rows, java.util.function.Function<T, String> field) {
		return String.join(",", rows.stream().map(field).toList());
	}
}
