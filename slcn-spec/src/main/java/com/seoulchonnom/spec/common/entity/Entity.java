package com.seoulchonnom.spec.common.entity;

import java.io.Serializable;
import java.util.UUID;

public abstract class Entity implements Serializable {
	protected String id;
	private long entityVersion;

	protected Entity() {
		this.id = UUID.randomUUID().toString();
	}

	protected Entity(String id) {
		this.id = id;
	}
}
