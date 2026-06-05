package com.seoulchonnom.spec.calendar.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CalendarRdo {
	private String id;
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
}
