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
public class ComplexMapListRdo {
	/** 조건에 맞는 전체 단지 수. items는 최대 300건이라 total이 더 클 수 있다. */
	private long total;
	private boolean truncated;
	private List<ComplexMapItemRdo> items;
}
