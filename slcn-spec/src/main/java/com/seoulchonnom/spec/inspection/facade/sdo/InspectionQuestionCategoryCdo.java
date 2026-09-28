package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * sortOrder가 0 이하면 맨 뒤(max+1)로 채번한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionCategoryCdo {
	private String name;
	private int sortOrder;
}
