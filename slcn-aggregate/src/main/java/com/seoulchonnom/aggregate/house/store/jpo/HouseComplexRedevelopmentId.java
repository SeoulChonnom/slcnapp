package com.seoulchonnom.aggregate.house.store.jpo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HouseComplexRedevelopmentId implements Serializable {
	private String complexId;
	private String redevelopmentId;
}
