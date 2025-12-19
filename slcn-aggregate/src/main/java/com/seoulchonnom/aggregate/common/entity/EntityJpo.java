package com.seoulchonnom.aggregate.common.entity;

import java.io.Serializable;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.NoArgsConstructor;

@MappedSuperclass
@NoArgsConstructor
public abstract class EntityJpo implements Serializable {
	@Id
	protected String id;
	@Version
	private long entityVersion;
}
