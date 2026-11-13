package com.seoulchonnom.spec.house.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompareRdo {
	/** 요청했지만 없는 complexId(요청 순서, 중복 없음). 없으면 빈 배열 */
	private List<String> missingIds;
	/** 요청 items 순서. 없는 단지는 건너뛴다 */
	private List<CompareColumnRdo> columns;
}
