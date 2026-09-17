package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 자동완성용. 사용 빈도 내림차순으로 내린다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionTagRdo {
	private String tagId;
	private String name;
	private int usageCount;
}
