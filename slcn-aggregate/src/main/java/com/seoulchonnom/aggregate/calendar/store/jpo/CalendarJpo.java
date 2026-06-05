package com.seoulchonnom.aggregate.calendar.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calendar", schema = "slcn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarJpo extends DomainEntityJpo {
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
