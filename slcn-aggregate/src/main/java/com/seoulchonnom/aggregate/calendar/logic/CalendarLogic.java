package com.seoulchonnom.aggregate.calendar.logic;

import static com.seoulchonnom.spec.calendar.constant.CalendarConstant.*;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.calendar.exception.CalendarScheduleConflictException;
import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;
import com.seoulchonnom.spec.calendar.mapper.CalendarMapper;
import com.seoulchonnom.spec.common.generator.IdGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarLogic {
	private final CalendarStore calendarStore;
	private final ScheduleStore scheduleStore;
	private final IdGenerator idGenerator;
	private final CalendarMapper calendarMapper;

	public List<CalendarRdo> getCalendars() {
		return calendarStore.findAllVisible().stream()
			.map(calendarMapper::toCalendarRdo)
			.toList();
	}

	@Transactional
	public CalendarRdo registerCalendar(CalendarCdo calendarCdo) {
		validateCalendarMutation(calendarCdo.getName(), calendarCdo.getBackgroundColor(), calendarCdo.getBorderColor(),
			calendarCdo.getTextColor(), calendarCdo.getSortOrder());

		String nextCalendarId = idGenerator.nextDomainId(SequenceName.CALENDAR.toString());
		Calendar calendar = calendarMapper.toCalendar(calendarCdo, nextCalendarId);
		calendarStore.save(calendar);
		return calendarMapper.toCalendarRdo(calendar);
	}

	@Transactional
	public CalendarRdo modifyCalendar(CalendarUdo calendarUdo) {
		if (!StringUtils.hasText(calendarUdo.getId())) {
			throw new BadRequestException("id는 필수입니다.");
		}

		validateCalendarMutation(calendarUdo.getName(), calendarUdo.getBackgroundColor(), calendarUdo.getBorderColor(),
			calendarUdo.getTextColor(), calendarUdo.getSortOrder());

		Calendar calendar = calendarStore.findById(calendarUdo.getId());
		calendarMapper.updateCalendar(calendarUdo, calendar);
		calendar.touchModifiedTime();
		calendarStore.save(calendar);
		return calendarMapper.toCalendarRdo(calendar);
	}

	@Transactional
	public void hideCalendar(String calendarId) {
		Calendar calendar = calendarStore.findById(calendarId);
		calendar.hideCalendar();
		calendarStore.save(calendar);
	}

	@Transactional
	public void deleteCalendar(String calendarId) {
		Calendar calendar = calendarStore.findById(calendarId);
		if (scheduleStore.existsByCalendarId(calendarId)) {
			throw new CalendarScheduleConflictException();
		}
		try {
			calendarStore.delete(calendar);
		} catch (DataIntegrityViolationException exception) {
			// existsByCalendarId 확인과 delete 사이에 다른 트랜잭션이 같은 캘린더로 일정을
			// 등록하면(M4 레이스) fk_schedule_calendar 위반이 이 시점에 발생한다. 애플리케이션
			// 전역 핸들러가 모든 DataIntegrityViolationException을 409로 매핑하지 않으므로
			// (다른 unique 제약과 섞이지 않도록) 여기서 좁혀서 변환한다.
			throw new CalendarScheduleConflictException();
		}
	}

	private void validateCalendarMutation(String name, String backgroundColor, String borderColor, String textColor,
		int sortOrder) {
		if (!StringUtils.hasText(name)) {
			throw new BadRequestException("name은 필수입니다.");
		}

		validateColor("backgroundColor", backgroundColor);
		validateColor("borderColor", borderColor);
		validateColor("textColor", textColor);

		if (sortOrder < 1) {
			throw new BadRequestException("sortOrder는 1 이상이어야 합니다.");
		}
	}

	private void validateColor(String fieldName, String color) {
		if (!StringUtils.hasText(color)) {
			throw new BadRequestException(fieldName + "는 필수입니다.");
		}

		if (!COLOR_HEX_PATTERN.matcher(color).matches()) {
			throw new BadRequestException(fieldName + "는 #RRGGBB 형식이어야 합니다.");
		}
	}
}
