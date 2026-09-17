package com.seoulchonnom.spec.inspection.facade.sdo;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionVisitStatusUdo {
	private InspectionStatus status;
}
