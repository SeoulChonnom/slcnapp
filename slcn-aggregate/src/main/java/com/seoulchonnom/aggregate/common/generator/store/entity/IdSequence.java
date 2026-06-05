package com.seoulchonnom.aggregate.common.generator.store.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "id_sequence", schema = "slcn")
public class IdSequence {
	@Id
	private String name;
	private String lastId;
}
