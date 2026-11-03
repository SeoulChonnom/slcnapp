package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.entity.vo.FarSource;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FarRdo {
	private BigDecimal pct;
	/** 총괄표제부(RECAP) 또는 표제부(TITLE) */
	private FarSource source;
}
