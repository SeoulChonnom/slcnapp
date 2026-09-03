# Schedule ICS Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish all non-hidden SLCN schedules as a read-only Apple/Google-compatible ICS subscription protected by independently revocable DB-backed secret URLs.

**Architecture:** JWT-protected management APIs create/list/delete opaque feed credentials, while a narrowly permitted GET endpoint authenticates a path token by SHA-256 hash lookup. A ScheduleFeedFlow loads all non-hidden recurrence masters with their Calendar metadata, and a dedicated iCal4j renderer produces a deterministic VCALENDAR with conditional ETag support.

**Tech Stack:** Java 17, Spring Boot, Spring Security, Spring Data JPA, Gradle, MapStruct, JUnit 5, Mockito, AssertJ, MockMvc, iCal4j 4.3.0

**Spec:** `docs/superpowers/specs/2026-09-03-schedule-ics-feed-design.md`

**Prerequisite:** Complete `docs/superpowers/plans/2026-09-03-schedule-recurrence.md` and its completion gate first.

## Global Constraints

- Calendar and Schedule remain shared global data; every valid feed token reads the same dataset.
- Publish only `Schedule.hidden=false`; Calendar.visible does not filter the feed.
- Use `Asia/Seoul`; timed VEVENT values use TZID and all-day values use exclusive DATE end.
- Include one deterministic `Asia/Seoul` VTIMEZONE component in VCALENDAR.
- ICS SUMMARY is `[{calendar.name}] {schedule.title}`.
- ICS DESCRIPTION contains only Schedule.body; omit it when body is empty.
- ICS CATEGORIES contains Calendar.name.
- Recurrence masters emit one VEVENT plus their raw RRULE; never expand occurrences in ICS.
- The feed is read-only and does not accept external mutations.
- Do not add Google/Apple SDKs, OAuth, CalDAV, webhook, or provider sync state.
- Token plaintext is returned only at creation and never persisted, logged, listed, placed in ETag, or echoed in errors.
- Invalid and deleted feed tokens return 404.
- Feed management APIs require ADMIN authority; unauthenticated requests return 401 and authenticated non-ADMIN requests return 403.
- Existing JWT Schedule/Calendar APIs remain protected and unchanged.
- Use tabs and existing repository conventions.

---

## File Structure

### Create

- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedToken.java`: persisted feed credential domain entity containing name and hash only.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedEvent.java`: immutable Schedule + Calendar projection consumed by the renderer.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/constant/ScheduleFeedConstant.java`: feed validation and not-found messages.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/ScheduleFeedFacade.java`: management and ICS HTTP contract.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedCdo.java`: token label input.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedCreatedRdo.java`: one-time feed URL response.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedRdo.java`: safe list response.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/jpo/ScheduleFeedTokenJpo.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/repository/ScheduleFeedTokenRepository.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/mapper/ScheduleFeedTokenJpoMapper.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/ScheduleFeedTokenStore.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenHasher.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenGenerator.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogic.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/exception/ScheduleFeedNotFoundException.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/exception/CalendarScheduleConflictException.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlow.java`
- `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java`
- `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRenderer.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/store/mapper/ScheduleFeedTokenJpoMapperTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/store/ScheduleFeedTokenStoreTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenHasherTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenGeneratorTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogicTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlowTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/calendar/store/CalendarStoreTest.java`
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceTest.java`
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceJsonContractTest.java`
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedIcsContractTest.java`
- `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRendererTest.java`
- `docs/schedule-ics-feed.md`: API, subscription, credential rotation, log redaction, and compatibility record.

### Modify

- `slcn-rest/build.gradle`: add iCal4j 4.3.0 for the wire renderer.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java`: add feed-not-found and Calendar-reference-conflict codes.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/constant/CalendarConstant.java`: add the Calendar-reference-conflict message.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`: load all non-hidden recurrence masters for the full feed.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`: expose full feed source query and Calendar reference existence.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/store/repository/CalendarRepository.java`: bulk lookup by IDs.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/store/CalendarStore.java`: expose bulk lookup.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/logic/CalendarLogic.java`: touch Calendar modifiedTime and reject deletion while schedules reference it.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/entity/Calendar.java`: expose modified-time touch.
- `slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java`: permit only the tokenized ICS GET route.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/calendar/logic/CalendarLogicTest.java`: cover rename timestamps and referenced-delete conflict.
- `slcn-boot/src/test/java/com/seoulchonnom/boot/common/config/SecurityConfigurationTest.java`: cover the narrow public GET matcher.

---

### Task 1: Add the Feed Token Domain and Hash-Only Persistence

**Files:**
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/constant/ScheduleFeedConstant.java`
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedToken.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/exception/ScheduleFeedNotFoundException.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/jpo/ScheduleFeedTokenJpo.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/repository/ScheduleFeedTokenRepository.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/mapper/ScheduleFeedTokenJpoMapper.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store/ScheduleFeedTokenStore.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/store/mapper/ScheduleFeedTokenJpoMapperTest.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/store/ScheduleFeedTokenStoreTest.java`

**Interfaces:**
- Domain fields: inherited ID/version/timestamps, `String name`, `String tokenHash`.
- Repository: `Optional<ScheduleFeedTokenJpo> findByTokenHash(String tokenHash)`.
- Store: `ScheduleFeedToken save(ScheduleFeedToken token)`, `List<ScheduleFeedToken> findAll()`, `Optional<ScheduleFeedToken> findByTokenHash(String tokenHash)`, `void deleteById(String id)`.
- Missing feed IDs and invalid/unknown raw tokens throw `ScheduleFeedNotFoundException`, backed by `ErrorCode.SCHEDULE_FEED_NOT_FOUND` (404).

- [ ] **Step 1: Write failing mapper and Store tests**

Assert name/hash and all managed fields round-trip through the mapper. In Store tests, verify `findByTokenHash`, deterministic `registeredTime` ordering for list, save, and delete behavior.

```java
assertThat(mapped.getTokenHash()).isEqualTo("sha256-hex");
assertThat(mapped.getName()).isEqualTo("Google Calendar");
```

- [ ] **Step 2: Run focused tests and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleFeedToken*Test'
```

Expected: FAIL because feed token types do not exist.

- [ ] **Step 3: Define the uniform 404 error and minimal domain/JPA model**

Add this error contract:

```java
// ScheduleFeedConstant
public static final String SCHEDULE_FEED_NOT_FOUND_ERROR_MESSAGE = "해당 일정 피드가 없습니다.";

// ErrorCode
SCHEDULE_FEED_NOT_FOUND(HttpStatus.NOT_FOUND, SCHEDULE_FEED_NOT_FOUND_ERROR_MESSAGE)

// aggregate feed exception
public class ScheduleFeedNotFoundException extends BusinessException {
	public ScheduleFeedNotFoundException() {
		super(ErrorCode.SCHEDULE_FEED_NOT_FOUND);
	}
}
```

Use a new `slcn.schedule_feed_token` table and unique hash:

```java
@Entity
@Table(
	name = "schedule_feed_token",
	schema = "slcn",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_schedule_feed_token_hash",
		columnNames = "token_hash"
	)
)
public class ScheduleFeedTokenJpo extends DomainEntityJpo {
	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;
}
```

Do not add plaintext token, active, expiry, owner, Calendar scope, or last-access columns.

- [ ] **Step 4: Implement mapper and Store methods**

Follow the existing Schedule/Calendar JPO mapper and Store patterns. A missing ID on delete must throw `ScheduleFeedNotFoundException` rather than leaking repository details.

- [ ] **Step 5: Run feed token persistence tests**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleFeedToken*Test'
```

Expected: PASS.

- [ ] **Step 6: Commit hash-only persistence**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity \
	slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/constant/ScheduleFeedConstant.java \
	slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java \
	slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/exception/ScheduleFeedNotFoundException.java \
	slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/store \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/store
git commit -m "feat: Schedule 피드 토큰 저장소 추가"
```

---

### Task 2: Generate, Hash, Validate, List, and Delete Feed Tokens

**Files:**
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenHasher.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenGenerator.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogic.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenHasherTest.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenGeneratorTest.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic/ScheduleFeedTokenLogicTest.java`

**Interfaces:**
- `String ScheduleFeedTokenHasher.hash(String rawToken)`: lowercase 64-character SHA-256 hex.
- `String ScheduleFeedTokenGenerator.generate()`: 32 random bytes, Base64 URL-safe without padding.
- `CreatedFeedToken create(String name)`: persisted entity plus one-time raw token.
- `List<ScheduleFeedToken> getAll()`.
- `void delete(String feedId)`.
- `void validate(String rawToken)`: return normally only when the hash exists; otherwise throw feed not found.

- [ ] **Step 1: Write failing crypto utility tests**

```java
assertThat(hasher.hash("known-token"))
	.isEqualTo("49e2e40e591e61357758299c8cee170fb9fa7da160ec8acf110a4a409d905aaf");

String token = generator.generate();
assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
assertThat(token).doesNotContain("=");
```

Keep this fixed test vector independent of the implementation; do not calculate the expected value with the production method.

- [ ] **Step 2: Write failing Logic tests**

Verify name blank/over 100 characters fails, create stores only the hash, list never exposes raw token, delete delegates by feed ID, and validate hashes before Store lookup.

```java
verify(store).save(argThat(saved ->
	!saved.getTokenHash().equals(rawToken)
		&& saved.getTokenHash().length() == 64
));
```

- [ ] **Step 3: Run tests and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleFeedToken*Test'
```

Expected: FAIL because generator, hasher, and Logic do not exist.

- [ ] **Step 4: Implement SHA-256 and SecureRandom utilities**

```java
public String generate() {
	byte[] bytes = new byte[32];
	secureRandom.nextBytes(bytes);
	return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}

public String hash(String rawToken) {
	byte[] digest = messageDigest().digest(rawToken.getBytes(StandardCharsets.UTF_8));
	return HexFormat.of().formatHex(digest);
}
```

Reject null/blank raw tokens before hashing. Never log method arguments.

- [ ] **Step 5: Implement minimal token Logic**

Use a small return record internal to aggregate:

```java
public record CreatedFeedToken(ScheduleFeedToken feedToken, String rawToken) {
}
```

Create exactly one token, hash it, persist the entity, and return plaintext only in this record. `validate` must throw the same not-found exception for blank, malformed, unknown, and deleted tokens.

- [ ] **Step 6: Run token tests**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleFeedToken*Test'
```

Expected: PASS.

- [ ] **Step 7: Commit token lifecycle Logic**

```bash
git add slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/logic \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/logic
git commit -m "feat: Schedule 피드 토큰 발급 및 검증 추가"
```

---

### Task 3: Expose JWT-Protected Feed Management APIs

**Files:**
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/ScheduleFeedFacade.java`
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedCdo.java`
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedCreatedRdo.java`
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/sdo/ScheduleFeedRdo.java`
- Create: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java`
- Create: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceTest.java`
- Create: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceJsonContractTest.java`

**Interfaces:**
- `POST /schedule/feeds` with `ScheduleFeedCdo{name}` returns 201 and `ScheduleFeedCreatedRdo{id,name,feedUrl,registeredTime}`.
- `GET /schedule/feeds` returns `List<ScheduleFeedRdo{id,name,registeredTime}>`.
- `DELETE /schedule/feeds/{feedId}` returns 204.
- These management routes require ADMIN authority; USER-only and CLIENT-only JWTs receive 403.

- [ ] **Step 1: Write failing Resource delegation tests**

Assert exact status codes, Logic calls, and the one-time URL shape:

```text
{currentContextPath}/schedule/feeds/{rawToken}/calendar.ics
```

Use `ServletUriComponentsBuilder.fromCurrentContextPath()` so reverse-proxy-aware request configuration supplies the external base URL; never derive it from token DB state.

- [ ] **Step 2: Write failing JSON contract tests**

Assert create JSON includes feedUrl but no tokenHash, while list JSON contains neither feedUrl nor tokenHash:

```java
mockMvc.perform(get("/schedule/feeds"))
	.andExpect(jsonPath("$[0].id").value(feedId))
	.andExpect(jsonPath("$[0].name").value("Google Calendar"))
	.andExpect(jsonPath("$[0].feedUrl").doesNotExist())
	.andExpect(jsonPath("$[0].tokenHash").doesNotExist());
```

- [ ] **Step 3: Run REST feed tests and observe failure**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleFeedResource*Test'
```

Expected: FAIL because facade, DTOs, and Resource do not exist.

- [ ] **Step 4: Implement DTOs, facade, and management methods**

Match existing facade/Resource annotations and response conventions. Validate name through both Bean Validation and Logic. Do not add a rotate endpoint: rotation is create-new, replace subscription URL, then delete-old.

- [ ] **Step 5: Run REST tests**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleFeedResource*Test'
```

Expected: PASS.

- [ ] **Step 6: Commit feed management API**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade \
  slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java \
  slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed
git commit -m "feat: Schedule 피드 관리 API 추가"
```

---

### Task 4: Load the Full Feed Dataset and Protect Calendar References

**Files:**
- Create: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity/ScheduleFeedEvent.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/repository/ScheduleRepository.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStore.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/store/repository/CalendarRepository.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/store/CalendarStore.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/entity/Calendar.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/logic/CalendarLogic.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/constant/CalendarConstant.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/exception/CalendarScheduleConflictException.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlow.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/store/ScheduleStoreTest.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/calendar/store/CalendarStoreTest.java`
- Modify: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/calendar/logic/CalendarLogicTest.java`
- Create: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/schedule/feed/flow/ScheduleFeedFlowTest.java`

**Interfaces:**
- `List<Schedule> ScheduleStore.findAllNonHiddenForFeed()` means `hidden=false`, independently of Calendar.visible.
- `boolean ScheduleStore.existsByCalendarId(String calendarId)`.
- `Map<String, Calendar> CalendarStore.findAllByIds(Collection<String> ids)`.
- `List<ScheduleFeedEvent> ScheduleFeedFlow.getFeedEvents(String rawToken)`.
- `ScheduleFeedEvent(Schedule schedule, Calendar calendar)`.
- A referenced Calendar delete throws `CalendarScheduleConflictException`, backed by `ErrorCode.CALENDAR_SCHEDULE_CONFLICT` (409).

- [ ] **Step 1: Write failing Store and Flow tests**

Verify the full-feed query includes old one-time schedules and recurrence masters, excludes hidden rows, bulk-loads Calendar IDs once, drops orphan schedules, and sorts by Schedule start then ID.

```java
verify(calendarStore).findAllByIds(Set.of("CALENDAR-0001", "CALENDAR-0002"));
verify(feedTokenLogic).validate(rawToken);
```

Assert token validation happens before Schedule/Calendar Store access.

- [ ] **Step 2: Write failing Calendar deletion and rename tests**

In CalendarLogicTest assert:

- deletion with `scheduleStore.existsByCalendarId(calendarId) == true` throws `CalendarScheduleConflictException` and does not call delete;
- deletion with no reference still deletes;
- modify changes Calendar.modifiedTime.

- [ ] **Step 3: Run focused tests and observe failure**

```bash
./gradlew :slcn-aggregate:test --tests '*ScheduleFeedFlowTest' \
  --tests '*CalendarLogicTest' \
  --tests '*ScheduleStoreTest' \
  --tests '*CalendarStoreTest'
```

Expected: FAIL because feed queries, Flow, bulk lookup, and deletion guard do not exist.

- [ ] **Step 4: Add minimal queries and domain projection**

Repository signatures:

```java
List<ScheduleJpo> findAllByHiddenFalseOrderByStartAscIdAsc();
boolean existsByCalendarId(String calendarId);
List<CalendarJpo> findAllByIdIn(Collection<String> ids);
```

Add a pure projection:

```java
public record ScheduleFeedEvent(Schedule schedule, Calendar calendar) {
}
```

- [ ] **Step 5: Implement validation-first Flow orchestration**

```java
public List<ScheduleFeedEvent> getFeedEvents(String rawToken) {
	feedTokenLogic.validate(rawToken);
	List<Schedule> schedules = scheduleStore.findAllNonHiddenForFeed();
	Map<String, Calendar> calendars = calendarStore.findAllByIds(
		schedules.stream().map(Schedule::getCalendarId).collect(toSet())
	);
	return schedules.stream()
		.filter(schedule -> calendars.containsKey(schedule.getCalendarId()))
		.map(schedule -> new ScheduleFeedEvent(schedule, calendars.get(schedule.getCalendarId())))
		.sorted(comparing((ScheduleFeedEvent value) -> value.schedule().getStart())
			.thenComparing(value -> value.schedule().getId()))
		.toList();
}
```

Log only orphan Schedule ID and Calendar ID; never log Schedule body or raw token.

- [ ] **Step 6: Add Calendar timestamp and deletion protection**

Add the exact conflict contract:

```java
// CalendarConstant
public static final String CALENDAR_SCHEDULE_CONFLICT_ERROR_MESSAGE =
	"일정이 연결된 캘린더는 삭제할 수 없습니다.";

// ErrorCode
CALENDAR_SCHEDULE_CONFLICT(
	HttpStatus.CONFLICT,
	CALENDAR_SCHEDULE_CONFLICT_ERROR_MESSAGE
)

// aggregate calendar exception
public class CalendarScheduleConflictException extends BusinessException {
	public CalendarScheduleConflictException() {
		super(ErrorCode.CALENDAR_SCHEDULE_CONFLICT);
	}

	public CalendarScheduleConflictException(String message) {
		super(ErrorCode.CALENDAR_SCHEDULE_CONFLICT, message);
	}
}
```

Add `touchModifiedTime()` to Calendar, invoke it after mapper updates, and inject ScheduleStore into CalendarLogic. Before `CalendarStore.delete`, call `ScheduleStore.existsByCalendarId`; throw `CalendarScheduleConflictException` when it returns true.

- [ ] **Step 7: Run aggregate feed source tests**

Run the command from Step 3.

Expected: PASS.

- [ ] **Step 8: Commit feed query and reference protection**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/entity \
	slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/entity/Calendar.java \
	slcn-spec/src/main/java/com/seoulchonnom/spec/calendar/constant/CalendarConstant.java \
	slcn-spec/src/main/java/com/seoulchonnom/spec/common/exception/ErrorCode.java \
	slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule \
  slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar \
  slcn-aggregate/src/test/java/com/seoulchonnom/aggregate
git commit -m "feat: Schedule 피드 조회 흐름 추가"
```

---

### Task 5: Render Deterministic RFC 5545 ICS

**Files:**
- Modify: `slcn-rest/build.gradle`
- Create: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRenderer.java`
- Create: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRendererTest.java`

**Interfaces:**
- `RenderedCalendar render(List<ScheduleFeedEvent> events)`.
- `RenderedCalendar(String body, String etag)`.
- ETag is quoted lowercase SHA-256 of canonical UTF-8 body.
- UID format: `{schedule.id}@slcn`.

- [ ] **Step 1: Add iCal4j to the REST module**

```groovy
implementation 'org.mnode.ical4j:ical4j:4.3.0'
```

Use the same version as Plan A. Do not add Google or Apple dependencies.

- [ ] **Step 2: Write failing renderer tests for the canonical event mapping**

Use fixed entity timestamps and verify the parsed result contains:

```ics
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//SLCN//Schedule Feed//KO
CALSCALE:GREGORIAN
METHOD:PUBLISH
BEGIN:VEVENT
UID:<schedule-id>@slcn
SUMMARY:[데이트] 저녁 약속
DESCRIPTION:성수동 식당 예약
CATEGORIES:데이트
LOCATION:성수동
END:VEVENT
END:VCALENDAR
```

Do not assert only with substring checks. Parse the generated body through iCal4j and assert component/property values.

- [ ] **Step 3: Add failing timed, all-day, recurrence, and Korean folding tests**

Verify:

- timed `DTSTART/DTEND` resolve to Asia/Seoul;
- VCALENDAR contains exactly one `VTIMEZONE` whose TZID is `Asia/Seoul`;
- all-day `DTSTART;VALUE=DATE:20260903` and exclusive `DTEND;VALUE=DATE:20260904`;
- recurrence emits exactly one VEVENT with the original RRULE;
- DESCRIPTION is absent for blank body;
- commas, semicolons, backslashes, and newlines survive a serialize/parse round-trip;
- all lines use CRLF, final CRLF exists, and folded UTF-8 lines do not split a multibyte character;
- deterministic input yields byte-identical DTSTAMP, body, and ETag.

- [ ] **Step 4: Run renderer tests and observe failure**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleIcsRendererTest'
```

Expected: FAIL because the renderer does not exist.

- [ ] **Step 5: Implement VCALENDAR and VEVENT creation**

The implementation must follow this mapping shape:

```java
String summary = "[" + calendar.getName() + "] " + schedule.getTitle();
String uid = schedule.getId() + "@slcn";
long lastModifiedMillis = Math.max(
	schedule.getModifiedTime(),
	calendar.getModifiedTime()
);
```

Build iCal4j `net.fortuna.ical4j.model.Calendar`/`VEvent` objects rather than concatenating unescaped content lines; use the fully qualified iCal4j Calendar name where needed to avoid collision with `com.seoulchonnom.spec.calendar.entity.Calendar`. Obtain `Asia/Seoul` from iCal4j's `TimeZoneRegistry`, add its VTIMEZONE once, and reuse that timezone for every timed event. Use `CalendarOutputter` for UTF-8 RFC output. Add RRULE to the event without expanding it. Set `SEQUENCE` from Schedule.entityVersion. Set both `LAST-MODIFIED` and `DTSTAMP` from the maximum of Schedule.modifiedTime and Calendar.modifiedTime, converted to UTC. Do not use request time anywhere in the ICS body.

- [ ] **Step 6: Compute an ETag independent of token**

```java
String etag = '"' + HexFormat.of().formatHex(
	MessageDigest.getInstance("SHA-256")
		.digest(body.getBytes(StandardCharsets.UTF_8))
) + '"';
```

The same Schedule/Calendar state must yield the same ETag across requests.

- [ ] **Step 7: Run renderer tests**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleIcsRendererTest'
```

Expected: PASS.

- [ ] **Step 8: Commit the renderer**

```bash
git add slcn-rest/build.gradle \
  slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRenderer.java \
  slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleIcsRendererTest.java
git commit -m "feat: Schedule ICS 변환기 추가"
```

---

### Task 6: Serve Token-Authenticated ICS With ETag

**Files:**
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/ScheduleFeedFacade.java`
- Modify: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResourceTest.java`
- Create: `slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedIcsContractTest.java`

**Interfaces:**
- `GET /schedule/feeds/{feedToken}/calendar.ics` produces `text/calendar; charset=UTF-8`.
- Accepts optional `If-None-Match`.
- Returns 200 with body/ETag/`Cache-Control: private, no-cache`, or 304 with no body.
- Invalid tokens throw `ScheduleFeedNotFoundException`; the existing `CommonExceptionHandler` reads its `ErrorCode` and returns the uniform 404 response without a new handler method.

- [ ] **Step 1: Write failing 200 and empty-calendar contract tests**

Assert an unauthenticated MVC request with a valid token returns:

```java
.andExpect(status().isOk())
.andExpect(content().contentTypeCompatibleWith("text/calendar"))
.andExpect(header().string("Cache-Control", containsString("private")))
.andExpect(header().string("ETag", expectedEtag))
.andExpect(content().string(renderedBody));
```

An empty event list must still contain a valid VCALENDAR.

- [ ] **Step 2: Write failing conditional GET and invalid-token tests**

Assert matching strong, weak, comma-separated, and `*` If-None-Match forms return 304 and empty body, following the existing FileResource behavior. Verify invalid token returns 404 and the renderer is never called.

- [ ] **Step 3: Run ICS contract tests and observe failure**

```bash
./gradlew :slcn-rest:test --tests '*ScheduleFeed*Test' \
  --tests '*ScheduleIcsRendererTest'
```

Expected: FAIL because the ICS GET mapping and ETag handling are missing.

- [ ] **Step 4: Implement the GET endpoint**

```java
@GetMapping(
	value = "/{feedToken}/calendar.ics",
	produces = "text/calendar; charset=UTF-8"
)
public ResponseEntity<String> getCalendar(
	@PathVariable String feedToken,
	@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
) {
	List<ScheduleFeedEvent> events = scheduleFeedFlow.getFeedEvents(feedToken);
	RenderedCalendar rendered = scheduleIcsRenderer.render(events);
	if (etagMatches(ifNoneMatch, rendered.etag())) {
		return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
			.eTag(unquote(rendered.etag()))
			.cacheControl(CacheControl.noCache().cachePrivate())
			.build();
	}
	return ResponseEntity.ok()
		.contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
		.eTag(unquote(rendered.etag()))
		.cacheControl(CacheControl.noCache().cachePrivate())
		.body(rendered.body());
}
```

An empty VCALENDAR has no VEVENT DTSTAMP, so its body and ETag remain stable. Do not use request time in the body, because that would invalidate ETag on every request.

- [ ] **Step 5: Run Resource and renderer tests**

Run the command from Step 3.

Expected: PASS.

- [ ] **Step 6: Commit ICS HTTP endpoint**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/feed/facade/ScheduleFeedFacade.java \
  slcn-rest/src/main/java/com/seoulchonnom/rest/schedule/feed/ScheduleFeedResource.java \
  slcn-rest/src/test/java/com/seoulchonnom/rest/schedule/feed
git commit -m "feat: Schedule ICS 구독 API 추가"
```

---

### Task 7: Open Only the Tokenized GET Route and Restrict Management to ADMIN

**Files:**
- Modify: `slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java`
- Modify: `slcn-boot/src/test/java/com/seoulchonnom/boot/common/config/SecurityConfigurationTest.java`

**Interfaces:**
- Permit unauthenticated GET matching `/schedule/feeds/*/calendar.ics` inside the `/api` context.
- Require ADMIN authority for POST/GET collection/DELETE management routes.
- DB token validation remains mandatory after Security permits entry.

- [ ] **Step 1: Write failing security tests**

Cover:

```text
GET    /schedule/feeds/valid-token/calendar.ics  without JWT -> reaches Resource
POST   /schedule/feeds                           without JWT -> 401
GET    /schedule/feeds                           without JWT -> 401
DELETE /schedule/feeds/feed-id                   without JWT -> 401
POST   /schedule/feeds                           with USER or CLIENT JWT -> 403
GET    /schedule/feeds                           with USER or CLIENT JWT -> 403
DELETE /schedule/feeds/feed-id                   with USER or CLIENT JWT -> 403
POST/GET/DELETE management routes                with ADMIN JWT -> reaches Resource
GET    /schedule                                 without JWT -> 401
```

Also assert an invalid DB feed token cannot be bypassed by presenting a valid USER or CLIENT JWT.

- [ ] **Step 2: Run SecurityConfigurationTest and observe failure**

```bash
./gradlew :slcn-boot:test --tests '*SecurityConfigurationTest'
```

Expected: tokenized ICS GET is rejected before reaching Resource.

- [ ] **Step 3: Add a method-specific narrow permit rule**

Place the rule before the default `anyRequest().hasAuthority("USER")`:

```java
.requestMatchers(HttpMethod.GET, "/schedule/feeds/*/calendar.ics").permitAll()
.requestMatchers("/schedule/feeds", "/schedule/feeds/*").hasAuthority("ADMIN")
```

Do not permit `/schedule/feeds/**` for every method. The existing JWT filter already lets requests without a token continue to the authorization layer, so leave `JwtAuthenticationFilter` unchanged.

- [ ] **Step 4: Resolve Cache-Control header conflicts**

Run the ICS contract through the full boot Security chain. If Spring Security's default writer adds `no-store`, exclude only the ICS matcher from that writer or use a dedicated header writer matching the existing FileResource pattern. Assert exactly one effective Cache-Control policy containing `private` and `no-cache`.

- [ ] **Step 5: Run boot and REST security tests**

```bash
./gradlew :slcn-boot:test --tests '*SecurityConfigurationTest' \
  :slcn-rest:test --tests '*ScheduleFeed*Test'
```

Expected: PASS.

- [ ] **Step 6: Commit the security boundary**

```bash
git add slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java \
	slcn-boot/src/test/java/com/seoulchonnom/boot/common/config/SecurityConfigurationTest.java
git commit -m "feat: ICS 피드 토큰 인증 경로 허용"
```

---

### Task 8: Document Secret URL Handling and Run the Full Automated Gate

**Files:**
- Create: `docs/schedule-ics-feed.md`

**Interfaces:**
- Operators know the exact token-path redaction requirement.
- API consumers know create/list/delete and subscription workflows.

- [ ] **Step 1: Document the complete subscription workflow**

Include:

```text
1. Authenticate to SLCN and POST /api/schedule/feeds.
2. Copy the returned feedUrl once.
3. Add it in Apple Calendar or Google Calendar's desktop web UI.
4. Create a replacement token before deleting an old one.
5. Expect Schedule changes after the external Calendar's next refresh.
```

- [ ] **Step 2: Document required log redaction**

Require proxies, ingress, APM, and access logs to replace:

```text
/api/schedule/feeds/{any-token}/calendar.ics
```

with:

```text
/api/schedule/feeds/***/calendar.ics
```

Document that HTTPS is mandatory and that anyone holding the URL can read the shared schedule.

- [ ] **Step 3: Run focused test suites**

```bash
./gradlew :slcn-spec:test :slcn-aggregate:test :slcn-rest:test :slcn-boot:test
```

Expected: PASS.

- [ ] **Step 4: Run the full repository test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Inspect the final diff for token leaks and scope drift**

```bash
git diff --check
git diff --stat
rg -n "rawToken|feedToken|tokenHash" slcn-* docs
```

Expected: raw token appears only in transient request/response and test fixtures; JPA contains only tokenHash; no Google/Apple OAuth or CalDAV implementation was added.

- [ ] **Step 6: Commit documentation and final test coverage**

```bash
git add docs/schedule-ics-feed.md \
  slcn-spec/src/test slcn-aggregate/src/test slcn-rest/src/test slcn-boot/src/test
git commit -m "docs: Schedule ICS 구독 방법 추가"
```

---

### Task 9: Perform Manual Apple and Google Compatibility Verification

**Files:**
- Modify: `docs/schedule-ics-feed.md` or the selected existing document with a dated compatibility matrix.

**Interfaces:**
- Validates behavior external services control and automated tests cannot guarantee.

- [ ] **Step 1: Prepare representative Schedules**

Create one of each through existing authenticated APIs:

```text
일반 timed 일정: 한글 title/body/location
종일 일정: one-day exclusive end
주간 반복 일정: FREQ=WEEKLY;BYDAY=TU;COUNT=3
빈 body 일정
긴 한글 body 일정: line folding 확인
```

- [ ] **Step 2: Subscribe from Apple Calendar**

Add the URL as a new Calendar subscription, choose iCloud location, and verify it appears on the paired iPhone. Record result for title prefix, CATEGORIES visibility where exposed, timed/all-day/recurrence, Korean text, and read-only behavior.

- [ ] **Step 3: Subscribe from Google Calendar**

Use desktop web `Other calendars -> From URL`, then verify the subscribed calendar appears in the mobile app for the same account. Record the same field matrix.

- [ ] **Step 4: Verify refresh-based mutations**

Modify one Schedule, hide one, and delete one. Trigger Apple manual refresh where supported and wait for Google-managed refresh. Verify modified UID remains stable and hidden/deleted events disappear after refresh.

- [ ] **Step 5: Verify credential revocation**

Delete the feed ID through the JWT management API and request the old URL directly:

```bash
curl -i 'https://<host>/api/schedule/feeds/<old-token>/calendar.ics'
```

Expected: 404. Existing cached Calendar data may remain, but no later update succeeds.

- [ ] **Step 6: Record compatibility results**

Add a dated table with Apple macOS/iOS and Google web/mobile versions, pass/fail results, observed refresh delay, and any client-specific display difference. Do not change server behavior merely to match one client's cosmetic rendering unless the RFC output is wrong.

- [ ] **Step 7: Commit the compatibility record**

```bash
git add docs/schedule-ics-feed.md
git commit -m "test: Apple Google ICS 호환성 결과 기록"
```

---

## Plan B Completion Gate

- `./gradlew test` reports BUILD SUCCESSFUL.
- Valid secret URLs work without SLCN JWT; invalid/deleted URLs return 404.
- Feed management routes require ADMIN authority; unauthenticated requests return 401 and USER/CLIENT-only requests return 403.
- DB and API inspection show no persisted or listed plaintext token.
- ICS round-trip tests cover Korean text, escaping, timed, all-day, recurrence, modification, hide, and delete.
- Calendar name appears in SUMMARY prefix and CATEGORIES, not DESCRIPTION.
- Schedule UID is stable across modifications.
- Apple and Google can subscribe and eventually reflect modification/hide/delete.
- Access-log redaction and token replacement instructions are documented.
