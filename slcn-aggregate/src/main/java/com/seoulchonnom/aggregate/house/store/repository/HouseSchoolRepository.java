package com.seoulchonnom.aggregate.house.store.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.store.jpo.HouseSchoolJpo;

/**
 * 학교 쓰기는 JPA save를 쓰지 않는다. 학교 1,300건을 SELECT 후 저장하면 행마다 왕복이 두 번이고,
 * ON CONFLICT 한 문장이면 키 충돌도 DB가 처리한다. 네이티브 쿼리는 @Version을 거치지 않으므로
 * entity_version은 INSERT 시 0, UPDATE 시 +1로 직접 쓴다.
 */
@Repository
public interface HouseSchoolRepository extends JpaRepository<HouseSchoolJpo, String> {
	/**
	 * school_code가 같은 학교가 있으면 이름·학교급·좌표·확인일을 갱신한다. 확인일(synced_date)이 매번 바뀌므로
	 * 항상 UPDATE가 나간다(180일에 한 번, 1,300행).
	 * 사람이 검증할 지점: uk_house_school_code 제약이 실제로 생겼는지, 같은 응답을 두 번 넣어도 행 수가 그대로인지,
	 * enum 문자열(level)과 date 캐스트.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT INTO slcn.house_school "
		+ "(id, entity_version, registered_time, modified_time, school_code, name, level, latitude, longitude, synced_date) "
		+ "VALUES (:id, 0, :now, :now, :schoolCode, :name, :level, :latitude, :longitude, CAST(:syncedDate AS date)) "
		+ "ON CONFLICT (school_code) DO UPDATE SET "
		+ " name = EXCLUDED.name, level = EXCLUDED.level, latitude = EXCLUDED.latitude, "
		+ " longitude = EXCLUDED.longitude, synced_date = EXCLUDED.synced_date, "
		+ " modified_time = EXCLUDED.modified_time, entity_version = slcn.house_school.entity_version + 1",
		nativeQuery = true)
	int upsert(@Param("id") String id, @Param("now") long now, @Param("schoolCode") String schoolCode,
		@Param("name") String name, @Param("level") String level, @Param("latitude") double latitude,
		@Param("longitude") double longitude, @Param("syncedDate") LocalDate syncedDate);

	/**
	 * 이번 목록에 없는 학교를 지운다. 코드는 쉼표로 이은 문자열 하나로 받는다(학교 ID는 영숫자라 쉼표가 없다).
	 * 빈 문자열이면 모든 학교가 지워지므로 호출하는 쪽이 빈 목록이면 부르지 않는다.
	 * 사람이 검증할 지점: string_to_array + <> ALL 문법, 이번 목록에 있는 학교가 지워지지 않는지.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "DELETE FROM slcn.house_school "
		+ "WHERE school_code <> ALL (string_to_array(CAST(:codes AS text), ','))",
		nativeQuery = true)
	int deleteExcept(@Param("codes") String codes);
}
