package com.seoulchonnom.spec.house.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.house.entity.vo.ComplexGroupBy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexSummaryRdo {
	private ComplexGroupBy groupBy;
	private List<ComplexSummaryItemRdo> items;
}
