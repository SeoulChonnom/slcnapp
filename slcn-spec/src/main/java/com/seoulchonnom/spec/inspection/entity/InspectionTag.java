package com.seoulchonnom.spec.inspection.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 임장 태그와 매물 태그가 공유하는 마스터. 용도 구분 필드를 두지 않는다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InspectionTag extends DomainEntity {
	private String name;
}
