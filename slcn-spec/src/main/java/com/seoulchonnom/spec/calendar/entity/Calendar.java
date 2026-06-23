package com.seoulchonnom.spec.calendar.entity;

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

	public void hideCalendar() {
		this.visible = false;
	}
}
