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
public class ComplexSearchRdo {
	private List<ComplexSearchItemRdo> items;
}
