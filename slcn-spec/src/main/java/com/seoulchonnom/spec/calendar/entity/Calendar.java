package com.seoulchonnom.spec.calendar.entity;

import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;
import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Calendar extends DomainEntity {
	private String name;
	private String backgroundColor;
	private String borderColor;
	private String textColor;
	private boolean visible;
	private boolean editable;
	private boolean startEditable;
	private boolean durationEditable;
	private boolean defaultSelected;
	private int sortOrder;

	public Calendar(CalendarCdo calendarCdo, String id) {
		super(id);
		this.name = calendarCdo.getName();
		this.backgroundColor = calendarCdo.getBackgroundColor();
		this.borderColor = calendarCdo.getBorderColor();
		this.textColor = calendarCdo.getTextColor();
		this.visible = true;
		this.editable = calendarCdo.isEditable();
		this.startEditable = calendarCdo.isStartEditable();
		this.durationEditable = calendarCdo.isDurationEditable();
		this.defaultSelected = calendarCdo.isDefaultSelected();
		this.sortOrder = calendarCdo.getSortOrder();
	}

	public void updateCalendar(CalendarUdo calendarUdo) {
		this.name = calendarUdo.getName();
		this.backgroundColor = calendarUdo.getBackgroundColor();
		this.borderColor = calendarUdo.getBorderColor();
		this.textColor = calendarUdo.getTextColor();
		this.editable = calendarUdo.isEditable();
		this.startEditable = calendarUdo.isStartEditable();
		this.durationEditable = calendarUdo.isDurationEditable();
		this.defaultSelected = calendarUdo.isDefaultSelected();
		this.sortOrder = calendarUdo.getSortOrder();
	}

	public void hideCalendar() {
		this.visible = false;
	}
}
