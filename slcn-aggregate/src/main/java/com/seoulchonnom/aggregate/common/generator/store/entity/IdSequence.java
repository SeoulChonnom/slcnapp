package com.seoulchonnom.aggregate.common.generator.store.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class IdSequence {
	@Id
	private String name;
	private String lastId;
}
