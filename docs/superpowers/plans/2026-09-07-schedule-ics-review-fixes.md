# Schedule ICS 리뷰 지적 사항 수정 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #5 검증에서 확인된 결함 9건(H1~L3)을 수정해 ICS 피드가 단일 일정 때문에 중단되지 않고, 무제한 쿼리·잘못된 피드 URL·캘린더 삭제 레이스가 제거된 상태로 만든다.

**Architecture:** `start < end` 불변식을 쓰기 매퍼 `@AfterMapping`과 DB CHECK/NOT NULL 두 계층으로 강제한다. Schedule의 soft delete를 제거해 hard delete로 단일화하면 캘린더 삭제 가드가 자동으로 정확해진다. ICS 피드는 월 초로 스냅한 시간 창으로 조회 범위를 제한해 ETag 안정성을 유지하면서 무제한 성장을 막는다.

**Tech Stack:** Java 17, Spring Boot 3, Gradle multi-module, JPA/Hibernate 6 (PostgreSQL, `ddl-auto=update`), MapStruct 1.5.3, Lombok, ical4j 4.3.0, JUnit 5 + Mockito + AssertJ

**Spec:** `docs/superpowers/specs/2026-09-07-schedule-ics-review-fixes-design.md`

## Global Constraints

- 들여쓰기는 **탭**. 파일당 최상위 클래스 하나. 패키지 루트는 `com.seoulchonnom`.
- 모듈 의존 방향 고정: `slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec`. `slcn-spec`은 최하위이므로 `BadRequestException`(slcn-aggregate)을 참조할 수 없다 — `IllegalArgumentException`을 쓴다.
- 명명 규칙: REST 어댑터 `*Resource`, 비즈니스 `*Logic`, 오케스트레이션 `*Flow`, 영속 `*Store`/`*Repository`/`*Jpo`, 매퍼 `*Mapper`, DTO `*Cdo`/`*Udo`/`*Rdo`/`*Sdo`.
- 테스트 클래스는 `*Test`, 메서드는 `action_shouldExpectedResult`.
- 새 프레임워크를 도입하지 않는다. Lombok과 MapStruct의 기존 사용 패턴을 따른다.
- 설정값은 `@Value("${slcn.…}")` 패턴을 따른다 (`FileLogic`, `WebConfig` 참조).
- 커밋 메시지는 Conventional Commits + 한국어 요약 (`feat:`, `fix:`, `refactor:`, `docs:`).
- 작업 브랜치: `feat/schedule-ics-feed`. 워크트리: `/Users/ia03060_mac/workspace/slcn/slcnapp/.worktrees/schedule-ics-feed`.
- **DB 스키마는 이 계획에서 변경하지 않는다.** `ddl-auto=update`가 처리하지 못하는 CHECK/NOT NULL/FK/DROP COLUMN은 스펙 8절의 수동 DDL 런북에 있으며 배포 절차에서 실행한다. 코드에는 의도만 표기한다.
- 모든 Gradle 명령은 래퍼(`./gradlew`)를 쓴다. 최종 확인은 `./gradlew test`.

---

## File Structure

| 파일 | 책임 | Task |
|------|------|------|
| `slcn-spec/…/schedule/facade/ScheduleFacade.java` | Schedule API 계약 | 1 |
| `slcn-rest/…/schedule/ScheduleResource.java` | Schedule HTTP 어댑터 | 1 |
| `slcn-aggregate/…/schedule/logic/ScheduleLogic.java` | Schedule 비즈니스 로직 | 1 |
| `slcn-spec/…/schedule/entity/Schedule.java` | Schedule 도메인 모델 | 1 |
| `slcn-aggregate/…/schedule/store/jpo/ScheduleJpo.java` | Schedule 영속 모델·스키마 의도 | 1, 3 |
| `slcn-aggregate/…/schedule/store/repository/ScheduleRepository.java` | Schedule 쿼리 | 1, 4 |
| `slcn-aggregate/…/schedule/store/ScheduleStore.java` | Schedule 영속 접근 | 1, 4 |
| `slcn-spec/…/schedule/mapper/ScheduleMapper.java` | DTO↔도메인 매핑 + 쓰기 불변식 | 1, 2 |
| `slcn-spec/…/schedule/constant/ScheduleConstant.java` | Schedule 상수·메시지 | 1 |
| `slcn-aggregate/…/schedule/feed/flow/ScheduleFeedFlow.java` | 피드 조회 오케스트레이션 | 4, 5 |
| `slcn-spec/…/schedule/feed/entity/ScheduleFeedContent.java` | 피드 이름 + 이벤트 묶음 (신규) | 5 |
| `slcn-aggregate/…/schedule/feed/logic/ScheduleFeedTokenLogic.java` | 피드 토큰 검증 | 5 |
| `slcn-rest/…/schedule/feed/ScheduleIcsRenderer.java` | ICS 렌더링 | 5, 7 |
| `slcn-rest/…/schedule/feed/ScheduleFeedResource.java` | 피드 HTTP 어댑터 | 6 |
| `slcn-rest/…/common/handler/CommonExceptionHandler.java` | 예외→HTTP 매핑 | 8 |
| `slcn-boot/src/main/resources/application.yml` | 설정 | 4, 6 |
| `docs/schedule-ics-feed.md`, `docs/module.md` | 문서 | 9 |

---

## Task 1: Schedule soft delete 제거

`hidden`은 복구 API가 없어 쓰기 전용이다. 제거하면 `CalendarLogic`의 `existsByCalendarId` 가드가 코드 변경 없이 정확해진다(M1 해소). 함께 죽은 코드 `ScheduleStore.findAllByDateRange`(프로덕션 미사용, 테스트만 참조)도 정리한다.

**Files:**
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/ScheduleFacade.java`
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/ScheduleResource.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/jpo/ScheduleJpo.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapper.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/constant/ScheduleConstant.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/ScheduleResourceTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogicTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/mapper/ScheduleJpoMapperTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlowTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/calendar/store/CalendarStoreTest.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRendererTest.java`
- Test: `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/ScheduleConstructorCompatibilityTest.java`
- Test: `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `Schedule` 7인자 생성자 `Schedule(String calendarId, String title, String body, boolean allDay, LocalDateTime start, LocalDateTime end, String location)`
  - `Schedule` 8인자 생성자 `Schedule(String calendarId, String title, String body, boolean allDay, LocalDateTime start, LocalDateTime end, String location, String recurrenceRule)`
  - `ScheduleStore.findAllForFeed()` → `List<Schedule>` (Task 4에서 창 파라미터를 받도록 다시 바뀐다)
  - `ScheduleStore.findCandidatesByDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd)` → `List<Schedule>` (시그니처 유지)
  - `ScheduleStore.existsByCalendarId(String calendarId)` → `boolean` (시그니처 유지, 의미가 정확해짐)

- [ ] **Step 1: 기존 테스트를 새 동작에 맞게 갱신**

`hidden` / `hideSchedule` 을 참조하는 테스트를 모두 찾는다.

```bash
cd /Users/ia03060_mac/workspace/slcn/slcnapp/.worktrees/schedule-ics-feed
grep -rn -e "hidden" -e "Hidden" -e "hideSchedule" \
  slcn-aggregate/src/test slcn-rest/src/test slcn-spec/src/test slcn-boot/src/test
```

각 테스트에서 다음을 적용한다.

- `Schedule.builder()….hidden(false).build()` → `.hidden(false)` 호출 제거
- `Schedule.builder()….hidden(true).build()` 로 "피드에서 제외됨"을 검증하던 케이스 → 해당 테스트 삭제 (숨김 개념이 사라짐)
- `verify(scheduleStore, never()).findAllByDateRange(any(), any())` → 해당 검증 줄 삭제 (`ScheduleLogicTest:87`, `:112`)
- `scheduleRepository.findAllByHiddenFalseOrderByStartAscIdAsc()` 스텁 → `findAllByOrderByStartAscIdAsc()`
- `scheduleRepository.findAllByStartBeforeAndEndAfterAndHiddenFalseAndRecurrenceRuleIsNull(...)` 스텁 → `findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull(...)`
- `scheduleRepository.findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(...)` 스텁 → `findAllByStartBeforeAndRecurrenceRuleIsNotNull(...)`
- `ScheduleResourceTest` 의 `hideSchedule` 테스트 → 삭제
- `ScheduleConstructorCompatibilityTest` → 아래 새 시그니처로 갱신

`ScheduleConstructorCompatibilityTest` 를 다음으로 교체한다.

```java
package com.seoulchonnom.spec.schedule;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;

class ScheduleConstructorCompatibilityTest {
	@Test
	void constructor_withoutRecurrenceRule_shouldLeaveRecurrenceRuleNull() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001",
			"저녁 약속",
			"성수동 식당 예약",
			false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			"성수동");

		assertThat(schedule.getRecurrenceRule()).isNull();
		assertThat(schedule.getTitle()).isEqualTo("저녁 약속");
	}

	@Test
	void constructor_withRecurrenceRule_shouldKeepRecurrenceRule() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001",
			"주간 회의",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null,
			"FREQ=WEEKLY");

		assertThat(schedule.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY");
	}
}
```

`ScheduleResourceTest` 에 hard delete가 유일한 삭제 경로임을 고정하는 테스트를 추가한다.

```java
	@Test
	void deleteSchedule_shouldReturnNoContent() throws Exception {
		willDoNothing().given(scheduleLogic).deleteSchedule("SCHEDULE-0001");

		mockMvc.perform(delete("/schedule/{scheduleId}", "SCHEDULE-0001"))
			.andExpect(status().isNoContent());

		then(scheduleLogic).should().deleteSchedule("SCHEDULE-0001");
	}

	@Test
	void hideSchedule_shouldNotBeMapped() throws Exception {
		mockMvc.perform(put("/schedule/{scheduleId}/hide", "SCHEDULE-0001"))
			.andExpect(status().isNotFound());
	}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console plain`
Expected: FAIL — 컴파일 오류 (`hidden(boolean)` 없음, `findAllByHiddenFalse…` 없음) 또는 `hideSchedule_shouldNotBeMapped` 실패

- [ ] **Step 3: `Schedule` 에서 hidden 제거**

`slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java` 를 다음으로 교체한다.

```java
package com.seoulchonnom.spec.schedule.entity;

import java.time.LocalDateTime;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Schedule extends DomainEntity {
	private String calendarId;

	private String title;

	private String body;

	private boolean allDay;

	private LocalDateTime start;

	private LocalDateTime end;

	private String location;

	private String recurrenceRule;

	public Schedule(
		String calendarId,
		String title,
		String body,
		boolean allDay,
		LocalDateTime start,
		LocalDateTime end,
		String location
	) {
		this(calendarId, title, body, allDay, start, end, location, null);
	}

	public void touchModifiedTime() {
		this.modifiedTime = System.currentTimeMillis();
	}
}
```

- [ ] **Step 4: `ScheduleJpo` 에서 hidden 제거**

`slcn-aggregate/…/schedule/store/jpo/ScheduleJpo.java` 에서 마지막 필드를 삭제한다.

```java
	@Column(name = "recurrence_rule", columnDefinition = "text")
	private String recurrenceRule;
-
-	private boolean hidden;
 }
```

> `hidden` 컬럼은 DB에 NOT NULL·기본값 없음으로 남아 있다. 스펙 9절 배포 순서 2단계(`SET DEFAULT false`)를 배포 전에 반드시 실행해야 한다.

- [ ] **Step 5: `ScheduleRepository` 쿼리에서 hidden 술어 제거**

`slcn-aggregate/…/schedule/store/repository/ScheduleRepository.java` 를 다음으로 교체한다.

```java
package com.seoulchonnom.aggregate.schedule.store.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;

public interface ScheduleRepository extends JpaRepository<ScheduleJpo, String> {
	Optional<ScheduleJpo> findById(String id);

	List<ScheduleJpo> findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull(
		LocalDateTime rangeEnd,
		LocalDateTime rangeStart
	);

	List<ScheduleJpo> findAllByStartBeforeAndRecurrenceRuleIsNotNull(LocalDateTime rangeEnd);

	List<ScheduleJpo> findAllByOrderByStartAscIdAsc();

	boolean existsByCalendarId(String calendarId);
}
```

`findAllByStartBeforeAndEndAfterAndHiddenFalse` 는 `ScheduleStore.findAllByDateRange` 에서만 쓰였고 그 메서드가 죽은 코드이므로 함께 삭제한다.

- [ ] **Step 6: `ScheduleStore` 정리**

`findAllByDateRange` 를 삭제하고 `findAllNonHiddenForFeed` 를 `findAllForFeed` 로 바꾼다.

```java
	public List<Schedule> findCandidatesByDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
		Map<String, ScheduleJpo> candidates = new LinkedHashMap<>();
		scheduleRepository.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull(rangeEnd, rangeStart)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		scheduleRepository.findAllByStartBeforeAndRecurrenceRuleIsNotNull(rangeEnd)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		return candidates.values().stream()
			.map(scheduleJpoMapper::toDomain)
			.toList();
	}

	public List<Schedule> findAllForFeed() {
		return scheduleRepository.findAllByOrderByStartAscIdAsc().stream()
			.map(scheduleJpoMapper::toDomain)
			.toList();
	}
```

`ScheduleFeedFlow:36` 의 호출부를 `scheduleStore.findAllForFeed()` 로 바꾼다.

- [ ] **Step 7: `ScheduleMapper` 의 hidden 매핑 제거**

```java
	@Mapping(target = "start", expression = "java(parseDateTime(scheduleCdo.getStart(), scheduleCdo.isAllDay()))")
	@Mapping(target = "end", expression = "java(parseDateTime(scheduleCdo.getEnd(), scheduleCdo.isAllDay()))")
-	@Mapping(target = "hidden", constant = "false")
	Schedule toSchedule(ScheduleCdo scheduleCdo);
```

```java
	@Mapping(target = "modifiedTime", ignore = true)
-	@Mapping(target = "hidden", ignore = true)
	@Mapping(target = "start", expression = "java(parseDateTime(scheduleUdo.getStart(), scheduleUdo.isAllDay()))")
```

- [ ] **Step 8: `ScheduleLogic` 에서 `hideSchedule` 제거**

```java
	@Transactional
-	public void hideSchedule(String scheduleId) {
-		Schedule schedule = scheduleStore.findById(scheduleId);
-		schedule.hideSchedule();
-		schedule.touchModifiedTime();
-		scheduleStore.save(schedule);
-	}
-
-	@Transactional
	public void deleteSchedule(String scheduleId) {
```

- [ ] **Step 9: Facade와 Resource에서 hide 엔드포인트 제거**

`ScheduleFacade` 에서:

```java
-	@Operation(summary = "일정 삭제(숨김)", description = "일정 목록 삭제 API")
-	ResponseEntity<Void> hideSchedule(String scheduleId);
-
-	@Operation(summary = "일정 완전 삭제", description = "일정 데이터 삭제 API")
+	@Operation(summary = "일정 삭제", description = "일정 삭제 API")
	ResponseEntity<Void> deleteSchedule(String scheduleId);
```

`ScheduleResource` 에서 `hideSchedule` 메서드 전체와 `@PutMapping("/{scheduleId}/hide")` 를 삭제한다. 사용하지 않게 된 `PutMapping` import가 남아 있으면 함께 제거한다 (`modifySchedule` 이 `@PutMapping` 을 쓰므로 import는 유지된다).

- [ ] **Step 10: `ScheduleConstant` 메시지 통합**

```java
	public static final String DELETE_SCHEDULE_COMPLETE_MESSAGE = "일정 삭제에 성공하였습니다.";
-	public static final String HARD_DELETE_SCHEDULE_COMPLETE_MESSAGE = "일정 데이터 삭제에 성공하였습니다.";
```

삭제 전에 참조가 없는지 확인한다.

```bash
grep -rn "HARD_DELETE_SCHEDULE_COMPLETE_MESSAGE" slcn-spec/src slcn-aggregate/src slcn-rest/src slcn-boot/src
```

참조가 있으면 `DELETE_SCHEDULE_COMPLETE_MESSAGE` 로 바꾼다.

- [ ] **Step 11: 전체 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS — `BUILD SUCCESSFUL`

- [ ] **Step 12: 커밋**

```bash
git add -A
git commit -m "refactor: Schedule soft delete 제거하고 hard delete로 단일화

복구 API가 없어 쓰기 전용이던 hidden 필드와 PUT /schedule/{id}/hide를
제거한다. 이로써 CalendarLogic의 existsByCalendarId 가드가 숨김 일정을
세지 않게 되어 캘린더 삭제가 영구 차단되던 문제가 해소된다.
사용되지 않던 ScheduleStore.findAllByDateRange도 함께 정리한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 2: 쓰기 매퍼에 `start < end` 불변식 추가

MapStruct 생성 코드가 `new Schedule()` + setter를 쓰므로(`Schedule` 에 `@NoArgsConstructor` 가 있어 무인자 생성자가 우선 선택됨) 생성자 검증은 호출되지 않는다. 검증은 쓰기 매퍼의 `@AfterMapping` 훅에 둔다. 복원 경로(`ScheduleJpoMapper.toDomain`)는 의도적으로 제외한다 — DB에 있는 행을 읽는 것까지 실패시키면 피드뿐 아니라 월별 조회까지 중단된다.

**Files:**
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapper.java`
- Test: `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/mapper/ScheduleJpoMapperTest.java`

**Interfaces:**
- Consumes: Task 1의 `Schedule` (hidden 제거된 상태)
- Produces: `ScheduleMapper.validateDateRange(@MappingTarget Schedule schedule)` — `toSchedule(ScheduleCdo)` 와 `updateSchedule(ScheduleUdo, Schedule)` 에 자동 적용되는 `@AfterMapping` 훅

- [ ] **Step 1: 실패하는 테스트 작성**

`slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java` 에 추가한다. `ScheduleMapperImpl` 은 MapStruct가 생성하므로 `new ScheduleMapperImpl()` 로 직접 만든다 (기존 테스트가 이미 그렇게 하고 있으면 그 방식을 따른다).

```java
	@Test
	void toSchedule_whenStartEqualsEnd_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("저녁 약속");
		scheduleCdo.setAllDay(false);
		scheduleCdo.setStart("2026-09-03T19:00:00+09:00");
		scheduleCdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("start는 end보다 빨라야 합니다.");
	}

	@Test
	void toSchedule_whenStartAfterEnd_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("저녁 약속");
		scheduleCdo.setAllDay(false);
		scheduleCdo.setStart("2026-09-03T20:00:00+09:00");
		scheduleCdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void toSchedule_whenAllDayEndIsExclusiveNextDay_shouldSucceed() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("휴가");
		scheduleCdo.setAllDay(true);
		scheduleCdo.setStart("2026-09-03");
		scheduleCdo.setEnd("2026-09-04");

		Schedule schedule = scheduleMapper.toSchedule(scheduleCdo);

		assertThat(schedule.getStart()).isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
		assertThat(schedule.getEnd()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0));
	}

	@Test
	void toSchedule_whenAllDayEndIsSameDay_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("휴가");
		scheduleCdo.setAllDay(true);
		scheduleCdo.setStart("2026-09-03");
		scheduleCdo.setEnd("2026-09-03");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updateSchedule_whenStartEqualsEnd_shouldThrowIllegalArgumentException() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0), null);

		ScheduleUdo scheduleUdo = new ScheduleUdo();
		scheduleUdo.setId("SCHEDULE-0001");
		scheduleUdo.setCalendarId("CALENDAR-0001");
		scheduleUdo.setTitle("저녁 약속");
		scheduleUdo.setAllDay(false);
		scheduleUdo.setStart("2026-09-03T19:00:00+09:00");
		scheduleUdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.updateSchedule(scheduleUdo, schedule))
			.isInstanceOf(IllegalArgumentException.class);
	}
```

복원 경로가 검증되지 **않는다**는 것도 고정한다. `slcn-aggregate/…/schedule/store/mapper/ScheduleJpoMapperTest.java` 에 추가한다.

```java
	@Test
	void toDomain_whenStoredRangeIsInvalid_shouldMapWithoutValidation() {
		ScheduleJpo scheduleJpo = new ScheduleJpo();
		scheduleJpo.setId("SCHEDULE-0001");
		scheduleJpo.setCalendarId("CALENDAR-0001");
		scheduleJpo.setTitle("레거시 일정");
		scheduleJpo.setAllDay(false);
		scheduleJpo.setStart(LocalDateTime.of(2026, 9, 3, 19, 0));
		scheduleJpo.setEnd(LocalDateTime.of(2026, 9, 3, 19, 0));

		Schedule schedule = scheduleJpoMapper.toDomain(scheduleJpo);

		assertThat(schedule.getStart()).isEqualTo(schedule.getEnd());
	}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :slcn-spec:test --tests '*ScheduleMapperTest*' --console plain`
Expected: FAIL — `toSchedule` 이 예외를 던지지 않아 `assertThatThrownBy` 가 실패

- [ ] **Step 3: `@AfterMapping` 훅 구현**

`ScheduleMapper` 에 import와 훅을 추가한다.

```java
import org.mapstruct.AfterMapping;
```

인터페이스 본문 끝, `parseDateTime` 아래에 추가한다.

```java
	@AfterMapping
	default void validateDateRange(@MappingTarget Schedule schedule) {
		LocalDateTime start = schedule.getStart();
		LocalDateTime end = schedule.getEnd();
		if (start == null || end == null || !start.isBefore(end)) {
			throw new IllegalArgumentException("start는 end보다 빨라야 합니다.");
		}
	}
```

`@MappingTarget Schedule` 이므로 MapStruct는 결과 타입이 `Schedule` 인 `toSchedule(ScheduleCdo)` 와 `updateSchedule(ScheduleUdo, Schedule)` 에만 이 훅을 삽입한다. 결과 타입이 `ScheduleRdo` 인 메서드에는 적용되지 않는다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :slcn-spec:test :slcn-aggregate:test --console plain`
Expected: PASS

생성된 코드에 훅이 실제로 삽입됐는지 확인한다.

```bash
grep -n "validateDateRange" \
  slcn-spec/build/generated/sources/annotationProcessor/java/main/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperImpl.java
```

Expected: `toSchedule` 과 `updateSchedule` 두 곳에서 호출됨. `toScheduleRdo` 에서는 호출되지 않음.

- [ ] **Step 5: 전체 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "fix: Schedule 쓰기 경로에 start<end 불변식 추가

MapStruct가 무인자 생성자와 setter를 사용하므로 생성자 검증은 호출되지
않는다. 쓰기 매퍼의 @AfterMapping 훅으로 toSchedule과 updateSchedule을
검증한다. 복원 경로인 ScheduleJpoMapper.toDomain은 의도적으로 제외해
저장된 행을 읽는 것까지 실패하지 않도록 한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 3: `ScheduleJpo` 에 스키마 의도 표기와 인덱스 추가

DB 제약은 수동 DDL이 담당하지만, 코드에도 의도를 남겨 신규 환경에서 `ddl-auto` 가 올바른 스키마를 만들도록 한다. 인덱스는 `ddl-auto=update` 가 기존 테이블에도 추가한다.

**Files:**
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/jpo/ScheduleJpo.java`

**Interfaces:**
- Consumes: Task 1의 `ScheduleJpo` (hidden 제거된 상태)
- Produces: 없음 (스키마 메타데이터만 변경)

- [ ] **Step 1: `@Table` 에 인덱스, 컬럼에 `nullable = false` 추가**

```java
import jakarta.persistence.Index;
```

```java
@Entity
@Table(
	name = "schedule",
	schema = "slcn",
	indexes = {
		@Index(name = "idx_schedule_start_end", columnList = "start_time,end_time"),
		@Index(name = "idx_schedule_calendar_id", columnList = "calendar_id")
	}
)
```

```java
	@Column(name = "start_time", nullable = false)
	private LocalDateTime start;

	@Column(name = "end_time", nullable = false)
	private LocalDateTime end;
```

> `ddl-auto=update` 는 기존 컬럼의 nullability를 바꾸지 않는다. 실제 NOT NULL 전환과 CHECK 제약은 스펙 8.2의 수동 DDL이 담당한다. 이 변경은 신규 환경과 코드 가독성을 위한 것이다.

- [ ] **Step 2: 전체 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS — 스키마 메타데이터 변경이므로 기존 테스트에 영향이 없어야 한다

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m "chore: Schedule 테이블 인덱스와 NOT NULL 의도 표기

start_time/end_time 조회와 calendar_id 존재 확인에 인덱스를 추가한다.
NOT NULL과 CHECK 제약의 실제 적용은 배포 시 수동 DDL이 담당한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 4: ICS 피드에 시간 창 도입

피드가 전체 일정을 매 요청마다 렌더하므로 데이터가 쌓일수록 응답이 선형 증가한다. 창을 도입하되 경계를 **월 초로 스냅**해 ETag가 같은 달 안에서 안정되도록 한다.

**Files:**
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlow.java`
- Modify: `slcn-boot/src/main/resources/application.yml`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlowTest.java`

**Interfaces:**
- Consumes: Task 1의 `ScheduleStore.findAllForFeed()`
- Produces:
  - `ScheduleStore.findFeedCandidates(LocalDateTime windowStart, LocalDateTime windowEnd)` → `List<Schedule>`
  - `ScheduleFeedFlow.feedWindow()` → `ScheduleFeedFlow.FeedWindow` (record: `LocalDateTime start`, `LocalDateTime end`), 월 초로 스냅됨

- [ ] **Step 1: 실패하는 테스트 작성**

`ScheduleStoreTest` 에 추가한다.

```java
	@Test
	void findFeedCandidates_shouldIncludeRecurringScheduleStartedBeforeWindow() {
		LocalDateTime windowStart = LocalDateTime.of(2026, 9, 1, 0, 0);
		LocalDateTime windowEnd = LocalDateTime.of(2028, 9, 1, 0, 0);

		ScheduleJpo recurring = new ScheduleJpo();
		recurring.setId("SCHEDULE-RECUR");
		recurring.setStart(LocalDateTime.of(2020, 1, 6, 10, 0));
		recurring.setEnd(LocalDateTime.of(2020, 1, 6, 11, 0));
		recurring.setRecurrenceRule("FREQ=WEEKLY");

		given(scheduleRepository.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(
			windowEnd, windowStart)).willReturn(List.of());
		given(scheduleRepository.findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(windowEnd))
			.willReturn(List.of(recurring));
		given(scheduleJpoMapper.toDomain(recurring)).willReturn(
			Schedule.builder().recurrenceRule("FREQ=WEEKLY")
				.start(recurring.getStart()).end(recurring.getEnd()).build());

		List<Schedule> result = scheduleStore.findFeedCandidates(windowStart, windowEnd);

		assertThat(result).hasSize(1);
	}

	@Test
	void findFeedCandidates_shouldExcludeNonRecurringScheduleOutsideWindow() {
		LocalDateTime windowStart = LocalDateTime.of(2026, 9, 1, 0, 0);
		LocalDateTime windowEnd = LocalDateTime.of(2028, 9, 1, 0, 0);

		given(scheduleRepository.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(
			windowEnd, windowStart)).willReturn(List.of());
		given(scheduleRepository.findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(windowEnd))
			.willReturn(List.of());

		List<Schedule> result = scheduleStore.findFeedCandidates(windowStart, windowEnd);

		assertThat(result).isEmpty();
	}
```

`ScheduleFeedFlowTest` 에 창 스냅 테스트를 추가한다.

```java
	@Test
	void feedWindow_shouldSnapBoundariesToMonthStart() {
		ScheduleFeedFlow.FeedWindow window = scheduleFeedFlow.feedWindow();

		assertThat(window.start().getDayOfMonth()).isEqualTo(1);
		assertThat(window.start().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(window.end().getDayOfMonth()).isEqualTo(1);
		assertThat(window.end().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(window.start()).isBefore(window.end());
	}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :slcn-aggregate:test --console plain`
Expected: FAIL — `findFeedCandidates`, `feedWindow`, `FeedWindow` 가 존재하지 않아 컴파일 오류

- [ ] **Step 3: 리포지토리 쿼리 추가**

`ScheduleRepository` 에서 `findAllByOrderByStartAscIdAsc()` **하나만** 다음 두 개로 교체한다.

```java
	List<ScheduleJpo> findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(
		LocalDateTime windowEnd,
		LocalDateTime windowStart
	);

	List<ScheduleJpo> findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(LocalDateTime windowEnd);
```

Task 1에서 만든 정렬 없는 두 메서드(`findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull`, `findAllByStartBeforeAndRecurrenceRuleIsNotNull`)는 **그대로 둔다.** 월별 조회(`findCandidatesByDateRange`)가 계속 사용하며, 정렬은 `ScheduleLogic.getSchedules` 가 확장 후에 수행하므로 DB 정렬이 불필요하다. 피드는 확장 없이 그대로 내보내므로 DB 정렬이 필요하다.

교체 후 리포지토리는 다음 6개 메서드를 갖는다.

```java
	Optional<ScheduleJpo> findById(String id);

	List<ScheduleJpo> findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull(
		LocalDateTime rangeEnd, LocalDateTime rangeStart);

	List<ScheduleJpo> findAllByStartBeforeAndRecurrenceRuleIsNotNull(LocalDateTime rangeEnd);

	List<ScheduleJpo> findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(
		LocalDateTime windowEnd, LocalDateTime windowStart);

	List<ScheduleJpo> findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(LocalDateTime windowEnd);

	boolean existsByCalendarId(String calendarId);
```

- [ ] **Step 4: `ScheduleStore.findFeedCandidates` 구현**

`findAllForFeed()` 를 다음으로 교체한다.

```java
	public List<Schedule> findFeedCandidates(LocalDateTime windowStart, LocalDateTime windowEnd) {
		Map<String, ScheduleJpo> candidates = new LinkedHashMap<>();
		scheduleRepository
			.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(windowEnd, windowStart)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		scheduleRepository
			.findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(windowEnd)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		return candidates.values().stream()
			.map(scheduleJpoMapper::toDomain)
			.toList();
	}
```

반복 일정은 창보다 이전에 시작해 창 안까지 이어질 수 있으므로 하한을 두지 않는다. RRULE은 원본 그대로 내보내므로 `UNTIL`/`COUNT` 해석은 구독 클라이언트의 몫이다.

- [ ] **Step 5: `ScheduleFeedFlow` 에 창 계산 추가**

import를 추가한다.

```java
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.SCHEDULE_ZONE_ID;
```

`@RequiredArgsConstructor` 는 `final` 필드만 생성자에 넣으므로, 설정값은 `final` 이 아닌 필드에 `@Value` 로 주입한다.

```java
	@Value("${slcn.ics.window.past-months:12}")
	private int windowPastMonths;

	@Value("${slcn.ics.window.future-months:24}")
	private int windowFutureMonths;

	public FeedWindow feedWindow() {
		LocalDateTime monthStart = LocalDateTime.now(SCHEDULE_ZONE_ID)
			.withDayOfMonth(1)
			.toLocalDate()
			.atStartOfDay();
		return new FeedWindow(
			monthStart.minusMonths(windowPastMonths),
			monthStart.plusMonths(windowFutureMonths));
	}

	public record FeedWindow(LocalDateTime start, LocalDateTime end) {
	}
```

`getFeedEvents` 의 조회 호출을 바꾼다.

```java
-		List<Schedule> schedules = scheduleStore.findAllForFeed();
+		FeedWindow window = feedWindow();
+		List<Schedule> schedules = scheduleStore.findFeedCandidates(window.start(), window.end());
```

- [ ] **Step 6: `application.yml` 에 설정 추가**

`slcn:` 블록에 추가한다.

```yaml
slcn:
  domain: "${SLCN_DOMAIN_LIST:http://localhost:9090}"
  upload:
    path: "${SLCN_UPLOAD_PATH}"
  ics:
    window:
      past-months: ${SLCN_ICS_WINDOW_PAST_MONTHS:12}
      future-months: ${SLCN_ICS_WINDOW_FUTURE_MONTHS:24}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS

- [ ] **Step 8: 커밋**

```bash
git add -A
git commit -m "perf: ICS 피드 조회에 월 초로 스냅한 시간 창 적용

전체 일정을 매 요청마다 렌더하던 것을 창 기반 조회로 바꾼다. 반복 일정은
창보다 이전에 시작해 창 안까지 이어질 수 있어 하한을 두지 않는다. 창
경계를 월 초로 스냅해 같은 달 안에서 ETag가 안정되도록 한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 5: 피드 이름을 `X-WR-CALNAME` 으로 출력

현재 피드에는 `X-WR-CALNAME` 이 없어 구독 시 클라이언트가 캘린더 이름을 URL로 표시할 수 있다. `ScheduleFeedToken.name` 을 렌더러까지 전달한다.

**Files:**
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedContent.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogic.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlow.java`
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRenderer.java`
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRendererTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlowTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogicTest.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceTest.java`

**Interfaces:**
- Consumes: Task 4의 `ScheduleFeedFlow.getFeedEvents(String rawToken)`
- Produces:
  - `record ScheduleFeedContent(String feedName, List<ScheduleFeedEvent> events)`
  - `ScheduleFeedTokenLogic.validate(String rawToken)` → `ScheduleFeedToken` (기존 `void` 에서 변경)
  - `ScheduleFeedFlow.getFeedContent(String rawToken)` → `ScheduleFeedContent` (기존 `getFeedEvents` 대체)
  - `ScheduleIcsRenderer.render(ScheduleFeedContent content)` → `RenderedCalendar` (기존 `render(List<ScheduleFeedEvent>)` 대체)

- [ ] **Step 1: 실패하는 테스트 작성**

`ScheduleIcsRendererTest` 에 추가한다.

```java
	@Test
	void render_shouldEmitCalendarNameAndTimeZoneProperties() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0001", "데이트", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null, null, 1, 1_757_000_000_000L, 1_756_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(
			new ScheduleFeedContent("가족 캘린더", List.of(fixture.event())));

		assertThat(rendered.body()).contains("X-WR-CALNAME:가족 캘린더");
		assertThat(rendered.body()).contains("X-WR-TIMEZONE:Asia/Seoul");
	}

	@Test
	void render_shouldProduceDifferentEtagForDifferentFeedName() {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0001", "데이트", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null, null, 1, 1_757_000_000_000L, 1_756_000_000_000L);

		String first = renderer.render(new ScheduleFeedContent("가족", List.of(fixture.event()))).etag();
		String second = renderer.render(new ScheduleFeedContent("회사", List.of(fixture.event()))).etag();

		assertThat(first).isNotEqualTo(second);
	}
```

`ScheduleFeedTokenLogicTest` 에 추가한다.

```java
	@Test
	void validate_whenTokenExists_shouldReturnFeedToken() {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name("가족 캘린더")
			.tokenHash("HASH")
			.build();
		given(scheduleFeedTokenHasher.hash("RAW-TOKEN")).willReturn("HASH");
		given(scheduleFeedTokenStore.findByTokenHash("HASH")).willReturn(Optional.of(token));

		ScheduleFeedToken result = scheduleFeedTokenLogic.validate("RAW-TOKEN");

		assertThat(result.getName()).isEqualTo("가족 캘린더");
	}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console plain`
Expected: FAIL — `ScheduleFeedContent` 없음, `render(ScheduleFeedContent)` 없음, `validate` 가 `void`

- [ ] **Step 3: `ScheduleFeedContent` 생성**

`slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedContent.java`

```java
package com.seoulchonnom.spec.schedule.feed.entity;

import java.util.List;

public record ScheduleFeedContent(String feedName, List<ScheduleFeedEvent> events) {
}
```

- [ ] **Step 4: `ScheduleFeedTokenLogic.validate` 가 토큰을 반환하도록 변경**

```java
-	public void validate(String rawToken) {
+	public ScheduleFeedToken validate(String rawToken) {
		if (rawToken == null || rawToken.isBlank() || containsWhitespace(rawToken)) {
			throw new ScheduleFeedNotFoundException();
		}

		String tokenHash;
		try {
			tokenHash = scheduleFeedTokenHasher.hash(rawToken);
		} catch (IllegalArgumentException exception) {
			throw new ScheduleFeedNotFoundException();
		}
-		if (scheduleFeedTokenStore.findByTokenHash(tokenHash).isEmpty()) {
-			throw new ScheduleFeedNotFoundException();
-		}
+		return scheduleFeedTokenStore.findByTokenHash(tokenHash)
+			.orElseThrow(ScheduleFeedNotFoundException::new);
	}
```

- [ ] **Step 5: `ScheduleFeedFlow.getFeedContent` 로 변경**

`getFeedEvents` 를 `getFeedContent` 로 교체한다. 메서드 전체는 다음과 같다.

```java
	public ScheduleFeedContent getFeedContent(String rawToken) {
		ScheduleFeedToken feedToken = feedTokenLogic.validate(rawToken);

		FeedWindow window = feedWindow();
		List<Schedule> schedules = scheduleStore.findFeedCandidates(window.start(), window.end());
		if (schedules.isEmpty()) {
			return new ScheduleFeedContent(feedToken.getName(), List.of());
		}

		Set<String> calendarIds = schedules.stream()
			.map(Schedule::getCalendarId)
			.filter(Objects::nonNull)
			.collect(toSet());
		Map<String, Calendar> calendars = calendarIds.isEmpty()
			? Map.of()
			: calendarStore.findAllByIds(calendarIds);

		List<ScheduleFeedEvent> events = schedules.stream()
			.filter(schedule -> {
				String calendarId = schedule.getCalendarId();
				if (calendarId != null && calendars.containsKey(calendarId)) {
					return true;
				}
				if (calendarId == null) {
					log.warn("Skipping orphan schedule: scheduleId={}", schedule.getId());
				} else {
					log.warn("Skipping orphan schedule: scheduleId={}, calendarId={}", schedule.getId(), calendarId);
				}
				return false;
			})
			.map(schedule -> new ScheduleFeedEvent(schedule, calendars.get(schedule.getCalendarId())))
			.sorted(comparing((ScheduleFeedEvent event) -> event.schedule().getStart())
				.thenComparing(event -> event.schedule().getId()))
			.toList();

		return new ScheduleFeedContent(feedToken.getName(), events);
	}
```

import에 `com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedContent` 와 `com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken` 을 추가한다.

- [ ] **Step 6: 렌더러가 `ScheduleFeedContent` 를 받도록 변경**

`ScheduleIcsRenderer` 의 시그니처와 프로퍼티 출력을 바꾼다.

```java
import net.fortuna.ical4j.model.property.XProperty;
```

기존 `render(List<ScheduleFeedEvent> events)` 를 다음으로 교체한다. 변경점은 시그니처, 첫 두 줄, 그리고 `seoulTimeZone.getVTimeZone()` 뒤에 삽입되는 두 프로퍼티뿐이며 나머지는 동일하다.

```java
	public RenderedCalendar render(ScheduleFeedContent content) {
		Objects.requireNonNull(content, "content");
		List<ScheduleFeedEvent> events = content.events();
		disableTimeZoneUpdates();
		TimeZoneRegistry timeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();
		net.fortuna.ical4j.model.TimeZone seoulTimeZone = timeZoneRegistry.getTimeZone(SCHEDULE_ZONE_ID.getId());
		if (seoulTimeZone == null) {
			throw new IllegalStateException("Asia/Seoul timezone is unavailable");
		}

		net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
		Version version = new Version();
		version.setMaxVersion(Version.VALUE_2_0);
		calendar.add(version);
		calendar.add(new ProdId(PROD_ID));
		calendar.add(new CalScale(CalScale.VALUE_GREGORIAN));
		calendar.add(new Method(Method.VALUE_PUBLISH));
		calendar.add(seoulTimeZone.getVTimeZone());
		if (hasText(content.feedName())) {
			calendar.add(new XProperty("X-WR-CALNAME", normalizeText(content.feedName())));
		}
		calendar.add(new XProperty("X-WR-TIMEZONE", SCHEDULE_ZONE_ID.getId()));

		events.stream()
			.sorted(eventComparator())
			.map(event -> toVEvent(event, timeZoneRegistry))
			.forEach(calendar::add);

		String body = output(calendar);
		return new RenderedCalendar(body, etag(body));
	}
```

`disableTimeZoneUpdates()` 호출은 이 태스크에서 **그대로 유지한다.** 제거는 Task 7에서 별도로 다룬다.

- [ ] **Step 7: `ScheduleFeedResource` 호출부 변경**

```java
-		List<ScheduleFeedEvent> events = scheduleFeedFlow.getFeedEvents(feedToken);
-		ScheduleIcsRenderer.RenderedCalendar rendered = scheduleIcsRenderer.render(events);
+		ScheduleFeedContent content = scheduleFeedFlow.getFeedContent(feedToken);
+		ScheduleIcsRenderer.RenderedCalendar rendered = scheduleIcsRenderer.render(content);
```

사용하지 않게 된 `ScheduleFeedEvent` import를 제거한다.

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS

ICS 파싱 계약 테스트(`ScheduleFeedIcsContractTest`)가 프로퍼티 개수를 단언한다면 함께 갱신한다.

- [ ] **Step 9: 커밋**

```bash
git add -A
git commit -m "feat: ICS 피드에 X-WR-CALNAME과 X-WR-TIMEZONE 출력

구독 시 클라이언트가 캘린더 이름을 URL로 표시하던 문제를 해결한다.
ScheduleFeedToken.name을 렌더러까지 전달하기 위해 ScheduleFeedContent를
도입하고 validate가 토큰을 반환하도록 바꾼다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 6: `SLCN_PUBLIC_BASE_URL` 로 피드 URL 조립

`forward-headers-strategy` 기본값이 `none` 이라 프록시 뒤에서는 `ServletUriComponentsBuilder` 가 내부 주소를 만든다. 설정값이 있으면 그것을 쓰고 없으면 현재 동작으로 폴백한다.

**Files:**
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java`
- Modify: `slcn-boot/src/main/resources/application.yml`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceTest.java`

**Interfaces:**
- Consumes: Task 5의 `ScheduleFeedResource`
- Produces: 없음 (응답 본문의 `feedUrl` 값만 달라짐)

- [ ] **Step 1: 실패하는 테스트 작성**

`ScheduleFeedResourceTest` 에 추가한다. 기존 테스트가 `@WebMvcTest` 또는 standalone MockMvc를 쓰는지 확인하고 그 방식에 맞춘다. `@Value` 필드는 `ReflectionTestUtils.setField` 로 주입한다.

```java
	private void givenCreatedFeedToken() {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name("가족 캘린더")
			.tokenHash("HASH")
			.build();
		token.setId("FEED-0001");
		given(scheduleFeedTokenLogic.create("가족 캘린더"))
			.willReturn(new ScheduleFeedTokenLogic.CreatedFeedToken(token, "RAW-TOKEN"));
	}

	@Test
	void createFeed_whenPublicBaseUrlConfigured_shouldUseItForFeedUrl() throws Exception {
		ReflectionTestUtils.setField(scheduleFeedResource, "publicBaseUrl", "https://slcn.example.com/api");
		givenCreatedFeedToken();

		mockMvc.perform(post("/schedule/feeds")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"가족 캘린더\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.feedUrl")
				.value("https://slcn.example.com/api/schedule/feeds/RAW-TOKEN/calendar.ics"));
	}

	@Test
	void createFeed_whenPublicBaseUrlBlank_shouldFallBackToRequestUrl() throws Exception {
		ReflectionTestUtils.setField(scheduleFeedResource, "publicBaseUrl", "");
		givenCreatedFeedToken();

		mockMvc.perform(post("/schedule/feeds")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"가족 캘린더\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.feedUrl").value(containsString("/schedule/feeds/RAW-TOKEN/calendar.ics")));
	}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :slcn-rest:test --tests '*ScheduleFeedResourceTest*' --console plain`
Expected: FAIL — `publicBaseUrl` 필드가 없어 `ReflectionTestUtils.setField` 가 예외

- [ ] **Step 3: 설정 주입과 URL 조립 구현**

`ScheduleFeedResource` 에 import와 필드를 추가한다.

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;
```

```java
	@Value("${slcn.public-base-url:}")
	private String publicBaseUrl;
```

`createFeed` 의 URL 조립을 바꾼다.

```java
	@Override
	@PostMapping
	public ResponseEntity<ScheduleFeedCreatedRdo> createFeed(@RequestBody @Valid ScheduleFeedCdo scheduleFeedCdo) {
		ScheduleFeedTokenLogic.CreatedFeedToken created = scheduleFeedTokenLogic.create(scheduleFeedCdo.getName());

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(ScheduleFeedCreatedRdo.from(created.feedToken(), feedUrl(created.rawToken())));
	}

	private String feedUrl(String rawToken) {
		UriComponentsBuilder builder = publicBaseUrl == null || publicBaseUrl.isBlank()
			? ServletUriComponentsBuilder.fromCurrentContextPath()
			: UriComponentsBuilder.fromUriString(publicBaseUrl.stripTrailing().replaceAll("/+$", ""));

		return builder
			.path("/schedule/feeds/{feedToken}/calendar.ics")
			.buildAndExpand(rawToken)
			.toUriString();
	}
```

- [ ] **Step 4: `application.yml` 에 설정 추가**

```yaml
slcn:
  domain: "${SLCN_DOMAIN_LIST:http://localhost:9090}"
  public-base-url: "${SLCN_PUBLIC_BASE_URL:}"
  upload:
    path: "${SLCN_UPLOAD_PATH}"
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "fix: 피드 URL을 SLCN_PUBLIC_BASE_URL로 조립

forward-headers-strategy 기본값이 none이라 프록시 뒤에서는 내부 주소가
반환됐다. 설정값이 있으면 그것으로 조립하고 없으면 기존 동작으로
폴백한다. 안전한 기본값은 유지한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 7: ical4j 전역 프로퍼티 설정을 1회로 축소

`render()` 가 매 요청마다 JVM 전역 프로퍼티를 건드린다. static 초기화 블록만 남긴다.

**Files:**
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRenderer.java`

**Interfaces:**
- Consumes: Task 5의 `ScheduleIcsRenderer.render(ScheduleFeedContent)`
- Produces: 없음

- [ ] **Step 1: `render()` 안의 호출 제거**

```java
	public RenderedCalendar render(ScheduleFeedContent content) {
		Objects.requireNonNull(content, "content");
-		disableTimeZoneUpdates();
		TimeZoneRegistry timeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();
```

static 블록과 `disableTimeZoneUpdates()` 메서드는 그대로 둔다.

- [ ] **Step 2: 테스트 통과 확인**

Run: `./gradlew :slcn-rest:test --console plain`
Expected: PASS

`ScheduleIcsRendererTest` 가 `TIMEZONE_UPDATE_PROPERTY` 를 단언한다면, static 블록이 클래스 로드 시 이미 설정하므로 그대로 통과해야 한다. 실패하면 테스트가 프로퍼티를 초기화한 뒤 `render()` 가 복구해 주기를 기대한 것이므로, 테스트에서 프로퍼티 초기화를 제거한다.

- [ ] **Step 3: 커밋**

```bash
git add -A
git commit -m "refactor: ical4j 타임존 갱신 차단을 static 초기화 1회로 축소

요청마다 JVM 전역 프로퍼티를 설정할 이유가 없다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 8: FK 위반을 409로 매핑

캘린더 삭제 레이스가 실제로 발생하면 `CalendarLogic` 의 가드를 통과한 뒤 DB FK가 거부한다. 이때 500이 아니라 409를 반환한다.

**Files:**
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/common/handler/CommonExceptionHandler.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/common/handler/CommonExceptionHandlerTest.java` (없으면 생성)

**Interfaces:**
- Consumes: 없음
- Produces: 없음

- [ ] **Step 1: 실패하는 테스트 작성**

`CommonExceptionHandlerTest` 를 생성하거나 기존 파일에 추가한다.

```java
	@Test
	void dataIntegrityViolationException_shouldReturnConflict() {
		CommonExceptionHandler handler = new CommonExceptionHandler();

		ResponseEntity<ErrorResponse> response = handler.dataIntegrityViolationException(
			new DataIntegrityViolationException("fk_schedule_calendar"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().getMessage())
			.isEqualTo(CALENDAR_SCHEDULE_CONFLICT_ERROR_MESSAGE);
	}
```

`ErrorResponse` 의 실제 접근자 이름을 확인해 맞춘다.

```bash
grep -n "class ErrorResponse" -A 20 slcn-spec/src/main/java/com/seoulchonnom/spec/common/response/ErrorResponse.java
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :slcn-rest:test --tests '*CommonExceptionHandlerTest*' --console plain`
Expected: FAIL — `dataIntegrityViolationException` 메서드 없음

- [ ] **Step 3: 핸들러 추가**

`CommonExceptionHandler` 에 import와 핸들러를 추가한다. `@ExceptionHandler(Exception.class)` **위에** 둔다 — Spring은 가장 구체적인 핸들러를 고르므로 순서가 동작을 바꾸지는 않지만 가독성을 위해서다.

```java
import org.springframework.dao.DataIntegrityViolationException;
```

```java
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> dataIntegrityViolationException(DataIntegrityViolationException e) {
		log.warn("Data integrity violation", e);
		return new ResponseEntity<>(
			ErrorResponse.from(false, ErrorCode.CALENDAR_SCHEDULE_CONFLICT.getMessage()),
			ErrorCode.CALENDAR_SCHEDULE_CONFLICT.getHttpStatus());
	}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console plain`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add -A
git commit -m "fix: FK 위반을 409로 매핑

캘린더 삭제 가드를 통과한 뒤 DB FK가 거부하는 레이스에서 500 대신
409를 반환한다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

---

## Task 9: 문서 갱신과 PR 본문 상세화

**Files:**
- Modify: `docs/schedule-ics-feed.md`
- Modify: `docs/module.md`
- Create: `/tmp/pr5-body.md` (임시, 커밋하지 않음)

**Interfaces:**
- Consumes: Task 1~8의 모든 변경
- Produces: 없음

- [ ] **Step 1: `docs/schedule-ics-feed.md` 갱신**

다음 내용을 반영한다.

- 종일 일정 `DTEND` exclusive 규약을 "API 계약"으로 명시 (기존에는 ICS 매핑 표에만 있었다)
- 시간 창 동작과 `SLCN_ICS_WINDOW_PAST_MONTHS` / `SLCN_ICS_WINDOW_FUTURE_MONTHS` 기본값, 창 밖 과거 일정이 구독 캘린더에서 사라진다는 점
- `SLCN_PUBLIC_BASE_URL` 설정과 `forward-headers-strategy` 와의 관계
- `X-WR-CALNAME` / `X-WR-TIMEZONE` 출력
- Schedule 삭제가 hard delete 단일 경로가 되었다는 점
- 배포 후 실기기 호환성 체크리스트 (Apple macOS/iOS, Google web/mobile: 등록, 갱신 지연, 수정·삭제 반영, 토큰 폐기)

- [ ] **Step 2: `docs/module.md` 에 규약 분기 기록**

Schedule은 hard delete, Travel은 `hidden` 기반 soft delete를 쓴다는 의도된 분기를 한 줄 남긴다.

- [ ] **Step 3: 문서 커밋**

```bash
git add docs/
git commit -m "docs: ICS 피드 시간 창·공개 URL·삭제 규약 문서 갱신

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01PE5CXSJiFX3PpeCj3vnPe4"
```

- [ ] **Step 4: PR 본문 작성**

`/tmp/pr5-body.md` 에 기존 본문을 내려받아 다음 세 섹션을 추가한다.

```bash
gh pr view 5 --json body --jq .body > /tmp/pr5-body.md
```

추가할 내용:

**호환성 파괴 변경**

| 변경 | 영향 |
|------|------|
| `start == end` 등록·수정이 400 | 0분 일정을 만들던 클라이언트 |
| 종일 일정 `end` 가 exclusive로 전환 | 프론트엔드 필수 변경 |
| `PUT /schedule/{id}/hide` 제거 | 프론트엔드 필수 변경 |
| `DELETE /schedule/{id}` 가 영구 삭제 | 복구 불가 |
| 참조 중인 캘린더 삭제가 409 | 관리 화면 |

**수동 DDL 절차** — 스펙 8절(`docs/superpowers/specs/2026-09-07-schedule-ics-review-fixes-design.md`)의 사전 점검·적용·롤백 SQL을 그대로 인용한다.

**배포 순서** — 스펙 9절의 7단계를 인용한다. `hidden` 컬럼에 `SET DEFAULT false` 를 먼저 적용해야 구·신 배포본 공존 구간을 넘길 수 있다는 점을 강조한다.

- [ ] **Step 5: PR 본문 반영**

```bash
gh pr edit 5 --body-file /tmp/pr5-body.md
gh pr view 5 --json body --jq .body | head -40
```

Expected: 추가한 세 섹션이 보인다

- [ ] **Step 6: 최종 검증**

```bash
./gradlew test --console plain
git log --oneline origin/main..HEAD
```

Expected: `BUILD SUCCESSFUL`, Task 1~9의 커밋이 모두 보인다

---

## 완료 조건

- [ ] `./gradlew test` 통과
- [ ] `grep -rn "hidden\|hideSchedule" slcn-spec/src/main slcn-aggregate/src/main slcn-rest/src/main` 결과에 Schedule 관련 항목 없음 (Travel은 남아 있어야 정상)
- [ ] `ScheduleMapperImpl` 생성 코드에서 `validateDateRange` 가 `toSchedule` 과 `updateSchedule` 에만 호출됨
- [ ] PR #5 본문에 호환성 파괴 변경·DDL 절차·배포 순서 섹션 존재
- [ ] 스펙 8절 DDL 런북이 배포 담당자에게 전달됨

## 이 계획에서 다루지 않는 것

스펙 11절의 후속 이슈이며 별도 작업으로 등록한다.

- RRULE `INTERVAL` 지원
- `recurrence_end` 파생 컬럼으로 종료된 반복 제외
- 렌더러 이벤트별 skip (스펙 1.4의 잔여 위험 해소)
- Schedule 복구 기능(휴지통 모델)
