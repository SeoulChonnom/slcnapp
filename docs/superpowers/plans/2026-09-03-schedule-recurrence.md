# Schedule Recurrence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store RFC 5545 recurrence rules on Schedule and expand matching occurrences in the existing bounded JSON schedule queries.

**Architecture:** Persist one Schedule as the recurrence master, validate its raw RRULE through iCal4j, and expand occurrences only for the requested `[start, end)` range. Keep mutation APIs series-based: `Schedule.id` remains the mutation key and each expanded response adds a stable `occurrenceId` for rendering.

**Tech Stack:** Java 17, Spring Boot, Gradle, Spring Data JPA, MapStruct, JUnit 5, Mockito, AssertJ, iCal4j 4.3.0

**Spec:** `docs/superpowers/specs/2026-09-03-schedule-ics-feed-design.md`

## Global Constraints

- Calendar and Schedule remain shared global data; do not add user ownership.
- All recurrence evaluation uses `Asia/Seoul`.
- Preserve the existing `/api/schedule/now` and `/api/schedule?start=&end=` routes and the one-month range limit.
- Store the RRULE value exactly as accepted; do not store expanded occurrences.
- Initial recurrence support is `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`, `COUNT`, `UNTIL`, `BYDAY`, and `BYMONTHDAY`.
- Do not support `EXDATE`, `RDATE`, `RECURRENCE-ID`, or single-occurrence mutation in this plan.
- Existing non-recurring JSON behavior must remain unchanged.
- Use tabs and the repository's existing Java/MapStruct conventions.

---

## File Structure

### Create

- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/ScheduleOccurrence.java`: immutable domain value for one rendered occurrence.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidator.java`: validates and parses supported RRULE values.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/exception/InvalidScheduleRecurrenceException.java`: maps invalid or unsupported RRULE values to the Schedule validation error.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpander.java`: expands one recurrence master inside a bounded range.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidatorTest.java`: supported/unsupported rule tests.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpanderTest.java`: occurrence boundary and all-day tests.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`: one-time and recurring candidate merge tests.
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/ScheduleResourceJsonContractTest.java`: recurrence request/response JSON contract.
- `docs/schedule-recurrence.md`: supported RRULE subset and series mutation semantics.

### Modify

- `slcn-aggregate/build.gradle`: add iCal4j 4.3.0.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java`: add the recurrence validation error code.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/constant/ScheduleConstant.java`: add the recurrence validation message.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`: persist raw `recurrenceRule` and expose a modified-time touch operation.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleCdo.java`: accept optional `recurrenceRule`.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleUdo.java`: accept optional `recurrenceRule`.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleRdo.java`: return optional `recurrenceRule` and `occurrenceId`.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapper.java`: map recurrence fields and occurrences.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/jpo/ScheduleJpo.java`: map `recurrence_rule`.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/mapper/ScheduleJpoMapper.java`: retain MapStruct round-trip mapping.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`: separate one-time overlap candidates from recurrence masters.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`: combine and deduplicate candidate queries.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java`: validate RRULE, expand reads, and touch modified time.
- `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java`: cover recurrence and occurrence mapping.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/mapper/ScheduleJpoMapperTest.java`: cover raw RRULE persistence round-trip.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogicTest.java`: cover mutation validation, expansion, ordering, and modified timestamps.
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/ScheduleResourceTest.java`: preserve existing delegation and status behavior.

---

### Task 1: Restore Recurrence Fields Through the Contract and Persistence Layers

**Files:**
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleCdo.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleUdo.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/facade/sdo/ScheduleRdo.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/jpo/ScheduleJpo.java`
- Modify: `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java`
- Modify: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/mapper/ScheduleJpoMapperTest.java`

**Interfaces:**
- Produces: nullable `String recurrenceRule` on Schedule, Cdo, Udo, Rdo, and JPO.
- Produces: nullable `String occurrenceId` on ScheduleRdo only.
- Preserves: all existing constructor, builder, getter, setter, and MapStruct behavior.

- [ ] **Step 1: Inspect the live PostgreSQL schema before changing mappings**

Run the project's normal database inspection path against the target environment:

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'slcn'
  AND table_name = 'schedule'
  AND column_name = 'recurrence_rule';
```

Expected: record whether the historical column is absent or present. If present, inspect null count and representative values; do not drop or rewrite it.

- [ ] **Step 2: Write failing mapper tests for recurrence round-trip**

Extend `ScheduleMapperTest` so a Cdo and a domain Schedule containing `FREQ=DAILY;COUNT=3` retain the exact string in the resulting Schedule/Rdo. Extend `ScheduleJpoMapperTest` with this assertion:

```java
assertThat(mapped.getRecurrenceRule()).isEqualTo("FREQ=DAILY;COUNT=3");
```

Also assert that a non-recurring Schedule maps `recurrenceRule == null` and `occurrenceId == null`.

- [ ] **Step 3: Run focused tests to verify they fail**

Run:

```bash
./gradlew :slcn-spec:test --tests '*ScheduleMapperTest' \
  :slcn-aggregate:test --tests '*ScheduleJpoMapperTest'
```

Expected: compilation failure because the recurrence fields do not exist.

- [ ] **Step 4: Add the minimal nullable fields**

Add this field consistently to Schedule, Cdo, Udo, Rdo, and JPO:

```java
private String recurrenceRule;
```

Add only to Rdo:

```java
private String occurrenceId;
```

Keep the existing JPO table and use an explicit column name if the surrounding JPO style does so:

```java
@Column(name = "recurrence_rule", columnDefinition = "text")
private String recurrenceRule;
```

Do not map `occurrenceId` to JPA; it is a calculated response value.

- [ ] **Step 5: Run mapper tests**

Run the command from Step 3.

Expected: PASS.

- [ ] **Step 6: Commit the contract restoration**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule \
  slcn-spec/src/test/java/com/seoulchonnom/spec/schedule \
  slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store
git commit -m "feat: Schedule 반복 규칙 필드 복구"
```

---

### Task 2: Validate the Supported RRULE Subset

**Files:**
- Modify: `slcn-aggregate/build.gradle`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/constant/ScheduleConstant.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/exception/InvalidScheduleRecurrenceException.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidator.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidatorTest.java`

**Interfaces:**
- Produces: `String validateAndNormalize(String recurrenceRule, boolean allDay)`; null/blank returns null, while a valid nonblank value is returned byte-for-byte.
- Produces: `Recur<LocalDateTime> parseTimed(String recurrenceRule)` for timed schedules.
- Produces: `Recur<LocalDate> parseAllDay(String recurrenceRule)` for all-day schedules.
- Throws: `InvalidScheduleRecurrenceException`, backed by `ErrorCode.INVALID_SCHEDULE_RECURRENCE` (400), when syntax, value types, or parts are unsupported.

- [ ] **Step 1: Add failing validator tests**

Cover at least these inputs:

```java
@ParameterizedTest
@ValueSource(strings = {
	"FREQ=DAILY;COUNT=3",
	"FREQ=WEEKLY;BYDAY=MO,WE,FR",
	"FREQ=MONTHLY;BYMONTHDAY=1;UNTIL=20261231T145959Z",
	"FREQ=YEARLY"
})
void validate_shouldAcceptSupportedRules(String rule) {
	assertThat(validator.validateAndNormalize(rule, false)).isEqualTo(rule);
}
```

Reject `FREQ=HOURLY`, `EXDATE`, `RDATE`, `INTERVAL`, `BYSECOND`, malformed values, and a rule containing both `COUNT` and `UNTIL`. Verify null and blank normalize as no recurrence. Add separate all-day tests for DATE-form `UNTIL` and timed tests for UTC DATE-TIME `UNTIL`.

- [ ] **Step 2: Run the validator test and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleRecurrenceRuleValidatorTest'
```

Expected: FAIL because the dependency and validator do not exist.

- [ ] **Step 3: Add the recurrence dependency**

In `slcn-aggregate/build.gradle`, add alongside existing implementation dependencies:

```groovy
implementation 'org.mnode.ical4j:ical4j:4.3.0'
```

- [ ] **Step 4: Implement the supported-part guard and iCal4j parsing**

Add the exact error contract first:

```java
// ScheduleConstant
public static final String INVALID_RECURRENCE_RULE_ERROR_MESSAGE = "올바르지 않은 반복 일정 규칙입니다.";

// ErrorCode
INVALID_SCHEDULE_RECURRENCE(
	HttpStatus.BAD_REQUEST,
	INVALID_RECURRENCE_RULE_ERROR_MESSAGE
)

// aggregate schedule exception
public class InvalidScheduleRecurrenceException extends BadRequestException {
	public InvalidScheduleRecurrenceException() {
		super(ErrorCode.INVALID_SCHEDULE_RECURRENCE);
	}
}
```

Accept only the RFC value part (for example `FREQ=WEEKLY;BYDAY=TU`), not an `RRULE:` prefix. Store that accepted string byte-for-byte without reordering or reformatting. Use one Spring component and reject unsupported keys before constructing the typed recurrence:

```java
private static final Set<String> SUPPORTED_KEYS = Set.of(
	"FREQ", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY"
);

public String validateAndNormalize(String recurrenceRule, boolean allDay) {
	if (!StringUtils.hasText(recurrenceRule)) {
		return null;
	}
	String value = recurrenceRule;
	Set<String> keys = Arrays.stream(value.split(";"))
		.map(this::keyOf)
		.collect(Collectors.toSet());
	if (!SUPPORTED_KEYS.containsAll(keys)) {
		throw new InvalidScheduleRecurrenceException();
	}
	RRule rule = new RRule(value);
	rule.validate();
	Recur<?> recur = rule.getRecur();
	if (!Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
		.contains(recur.getFrequency().name())) {
		throw new InvalidScheduleRecurrenceException();
	}
	validateUntilType(recur, allDay);
	return value;
}
```

`parseTimed` and `parseAllDay` must call this same validation path, then return the correctly typed value from `RRule.getRecur()`. Catch iCal4j parse/validation exceptions and wrap them as `InvalidScheduleRecurrenceException`; do not echo the supplied rule into logs or errors.

- [ ] **Step 5: Run validator and aggregate tests**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleRecurrenceRuleValidatorTest' \
  --tests '*ScheduleLogicTest'
```

Expected: PASS.

- [ ] **Step 6: Commit recurrence validation**

```bash
git add slcn-aggregate/build.gradle \
	slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java \
	slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/constant/ScheduleConstant.java \
	slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/exception/InvalidScheduleRecurrenceException.java \
	slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidator.java \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceRuleValidatorTest.java
git commit -m "feat: Schedule 반복 규칙 검증 추가"
```

---

### Task 3: Expand Recurrence Masters Into Bounded Occurrences

**Files:**
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/ScheduleOccurrence.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpander.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpanderTest.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapper.java`
- Modify: `slcn-spec/src/test/java/com/seoulchonnom/spec/schedule/mapper/ScheduleMapperTest.java`

**Interfaces:**
- Produces: `ScheduleOccurrence(Schedule schedule, LocalDateTime start, LocalDateTime end, String occurrenceId)`.
- Produces: `List<ScheduleOccurrence> expand(Schedule schedule, LocalDateTime rangeStart, LocalDateTime rangeEnd)`.
- Produces: `ScheduleRdo toScheduleRdo(ScheduleOccurrence occurrence)`.
- Occurrence ID format: `{scheduleId}/{start-at-Asia-Seoul-offset}`.

- [ ] **Step 1: Write failing expander tests**

Test a weekly master starting before the requested month:

```java
Schedule schedule = recurringSchedule(
	"2026-01-06T19:00:00+09:00",
	"2026-01-06T20:00:00+09:00",
	"FREQ=WEEKLY;BYDAY=TU"
);

List<ScheduleOccurrence> result = expander.expand(
	schedule,
	LocalDateTime.of(2026, 9, 1, 0, 0),
	LocalDateTime.of(2026, 10, 1, 0, 0)
);

assertThat(result).extracting(ScheduleOccurrence::start)
	.containsExactly(
		LocalDateTime.of(2026, 9, 1, 19, 0),
		LocalDateTime.of(2026, 9, 8, 19, 0),
		LocalDateTime.of(2026, 9, 15, 19, 0),
		LocalDateTime.of(2026, 9, 22, 19, 0),
		LocalDateTime.of(2026, 9, 29, 19, 0)
	);
```

Also test duration preservation, `[start, end)` overlap, COUNT, UNTIL, monthly/yearly, all-day end-exclusive behavior, and null recurrence returning one occurrence only when it overlaps.

- [ ] **Step 2: Run tests to verify failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleRecurrenceExpanderTest'
```

Expected: FAIL because the occurrence and expander types do not exist.

- [ ] **Step 3: Add the immutable occurrence type**

```java
public record ScheduleOccurrence(
	Schedule schedule,
	LocalDateTime start,
	LocalDateTime end,
	String occurrenceId
) {
}
```

- [ ] **Step 4: Implement bounded expansion**

Calculate the master duration once, request recurrence starts only inside the bounded search window extended backwards by that duration, and filter with the same overlap rule as the repository:

```java
public List<ScheduleOccurrence> expand(
	Schedule schedule,
	LocalDateTime rangeStart,
	LocalDateTime rangeEnd
) {
	Duration duration = Duration.between(schedule.getStart(), schedule.getEnd());
	if (!StringUtils.hasText(schedule.getRecurrenceRule())) {
		return overlaps(schedule.getStart(), schedule.getEnd(), rangeStart, rangeEnd)
			? List.of(occurrence(schedule, schedule.getStart(), schedule.getEnd(), null))
			: List.of();
	}

	return recurrenceStarts(schedule, rangeStart.minus(duration), rangeEnd).stream()
		.map(start -> occurrence(schedule, start, start.plus(duration), occurrenceId(schedule, start)))
		.filter(value -> overlaps(value.start(), value.end(), rangeStart, rangeEnd))
		.toList();
}
```

Use the validator's iCal4j parser; never iterate past `rangeEnd`. All-day masters still use stored midnight boundaries, so duration preserves exclusive end dates.

- [ ] **Step 5: Map occurrence values without mutating Schedule**

Add a MapStruct overload or default method that takes `ScheduleOccurrence`, copies the nested Schedule fields, overrides start/end, and sets occurrenceId. Keep the existing `toScheduleRdo(Schedule)` method for mutation responses.

- [ ] **Step 6: Run expander and mapper tests**

```bash
./gradlew :slcn-spec:test --tests '*ScheduleMapperTest' \
  :slcn-aggregate:test --tests '*ScheduleRecurrenceExpanderTest'
```

Expected: PASS.

- [ ] **Step 7: Commit bounded recurrence expansion**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule \
  slcn-spec/src/test/java/com/seoulchonnom/spec/schedule \
  slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpander.java \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleRecurrenceExpanderTest.java
git commit -m "feat: 반복 Schedule 발생 일정 계산 추가"
```

---

### Task 4: Make Schedule Candidate Queries Recurrence-Aware

**Files:**
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`

**Interfaces:**
- Produces: `List<Schedule> findCandidatesByDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd)`.
- One-time candidates satisfy `start < rangeEnd && end > rangeStart && hidden=false && recurrenceRule is null`.
- Recurrence candidates satisfy `start < rangeEnd && hidden=false && recurrenceRule is not null`.

- [ ] **Step 1: Write a failing Store test**

Mock separate repository results and verify both are mapped, merged, and deduplicated by Schedule ID in deterministic ID order. Include a recurrence master whose original end is before `rangeStart`.

```java
verify(repository).findAllByStartBeforeAndEndAfterAndHiddenFalseAndRecurrenceRuleIsNull(
	rangeEnd, rangeStart
);
verify(repository).findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(rangeEnd);
```

- [ ] **Step 2: Run the Store test and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleStoreTest'
```

Expected: FAIL because the candidate methods do not exist.

- [ ] **Step 3: Add the two repository queries**

Declare Spring Data methods with the exact predicates from the Interfaces block. Do not modify, overload, or repurpose the old method until all callers are migrated.

- [ ] **Step 4: Implement Store merge and deduplication**

```java
public List<Schedule> findCandidatesByDateRange(
	LocalDateTime rangeStart,
	LocalDateTime rangeEnd
) {
	Map<String, ScheduleJpo> candidates = new LinkedHashMap<>();
	repository.findAllByStartBeforeAndEndAfterAndHiddenFalseAndRecurrenceRuleIsNull(rangeEnd, rangeStart)
		.forEach(jpo -> candidates.put(jpo.getId(), jpo));
	repository.findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(rangeEnd)
		.forEach(jpo -> candidates.put(jpo.getId(), jpo));
	return candidates.values().stream()
		.map(scheduleJpoMapper::toDomain)
		.toList();
}
```

Sort the final expanded responses by occurrence start and occurrenceId in ScheduleLogic; Store ordering is not an API guarantee.

- [ ] **Step 5: Run Store and mapper tests**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleStoreTest' \
  --tests '*ScheduleJpoMapperTest'
```

Expected: PASS.

- [ ] **Step 6: Commit candidate queries**

```bash
git add slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java
git commit -m "feat: 반복 Schedule 조회 후보 확장"
```

---

### Task 5: Integrate Recurrence With Schedule Logic and Mutation Timestamps

**Files:**
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java`
- Modify: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogicTest.java`

**Interfaces:**
- Schedule exposes `void touchModifiedTime()`.
- ScheduleLogic validates recurrence on register and modify.
- `getSchedulesForMonth` and `getSchedulesForRange` return expanded and deterministically sorted ScheduleRdo values.
- Modify/hide update `modifiedTime`; register retains constructor registration timestamps.

- [ ] **Step 1: Write failing Logic tests**

Add tests proving:

```java
String registeredRule = validator.validateAndNormalize(
	cdo.getRecurrenceRule(), cdo.isAllDay()
);
String modifiedRule = validator.validateAndNormalize(
	udo.getRecurrenceRule(), udo.isAllDay()
);
verify(store).findCandidatesByDateRange(rangeStart, rangeEnd);
```

Assert an old recurrence master produces September occurrences, non-recurring results are unchanged, result order is start then occurrenceId, and modify/hide increase `modifiedTime`.

- [ ] **Step 2: Run ScheduleLogicTest and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleLogicTest'
```

Expected: FAIL because Logic still calls the old range query and does not validate/expand/touch.

- [ ] **Step 3: Add Schedule timestamp touch**

```java
public void touchModifiedTime() {
	this.modifiedTime = System.currentTimeMillis();
}
```

Call it after mapper-driven mutation and when hiding. Do not touch timestamps during reads.

- [ ] **Step 4: Integrate validation and expansion**

Factor one private method used by month and explicit-range reads:

```java
private List<ScheduleRdo> getSchedules(
	LocalDateTime rangeStart,
	LocalDateTime rangeEnd
) {
	return scheduleStore.findCandidatesByDateRange(rangeStart, rangeEnd).stream()
		.flatMap(schedule -> recurrenceExpander.expand(schedule, rangeStart, rangeEnd).stream())
		.sorted(comparing(ScheduleOccurrence::start)
			.thenComparing(value -> value.occurrenceId() == null
				? value.schedule().getId()
				: value.occurrenceId()))
		.map(scheduleMapper::toScheduleRdo)
		.toList();
}
```

Call `recurrenceRuleValidator.validateAndNormalize(...)` inside existing mutation validation. Apply its return value to the newly mapped Schedule so blank becomes null and a valid nonblank value remains unchanged. Preserve calendar, title, and start/end validation ordering unless a recurrence-specific failure is reached.

- [ ] **Step 5: Run aggregate Schedule tests**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleLogicTest' \
  --tests '*ScheduleRecurrence*Test' \
  --tests '*ScheduleStoreTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Logic integration**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java \
  slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogicTest.java
git commit -m "feat: Schedule 조회에 반복 일정 반영"
```

---

### Task 6: Lock the JSON Contract and Run Recurrence Regression

**Files:**
- Modify: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/ScheduleResourceTest.java`
- Create: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/ScheduleResourceJsonContractTest.java`
- Create: `docs/schedule-recurrence.md`

**Interfaces:**
- Existing routes and status codes are unchanged.
- Request JSON accepts optional `recurrenceRule`.
- Response JSON adds nullable `recurrenceRule` and `occurrenceId`.

- [ ] **Step 1: Add failing MockMvc contract tests**

Post and put JSON with:

```json
{
  "calendarId": "CALENDAR-0001",
  "title": "주간 약속",
  "body": "",
  "start": "2026-09-01T19:00:00+09:00",
  "end": "2026-09-01T20:00:00+09:00",
  "allDay": false,
  "location": "",
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU"
}
```

Assert the resource delegates the exact rule and serializes recurrenceRule/occurrenceId without removing existing fields.

- [ ] **Step 2: Run REST Schedule tests and observe failure**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleResource*Test'
```

Expected: FAIL until the new fields are bound by the contract.

- [ ] **Step 3: Make only contract-level adjustments required by the tests**

Keep Resource delegation unchanged if Lombok/Jackson binds the added fields automatically. Create `docs/schedule-recurrence.md` with the supported RRULE subset, JSON examples, series-wide mutation rule, and occurrenceId meaning.

- [ ] **Step 4: Run module tests**

```bash
./gradlew :slcn-spec:test :slcn-aggregate:test :slcn-rest:test
```

Expected: PASS.

- [ ] **Step 5: Run the full recurrence gate**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL with no regression in Calendar or Schedule tests.

- [ ] **Step 6: Commit the JSON contract**

```bash
git add slcn-rest/src/test/java/com/seoulchonnom/rest/schedule \
  docs/schedule-recurrence.md
git commit -m "test: 반복 Schedule API 계약 보강"
```

---

## Plan A Completion Gate

- All existing and new tests pass with `./gradlew test`.
- A recurrence master beginning before the requested month appears as occurrences inside the month.
- Every expanded occurrence has a stable occurrenceId while mutation still uses the master Schedule ID.
- Invalid or unsupported rules return the established Schedule validation response.
- No occurrence rows are persisted.
- The live schema result from Task 1 is recorded in the implementation handoff.
