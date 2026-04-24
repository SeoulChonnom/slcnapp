package com.seoulchonnom.spec.common.entity;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Entity implements JsonSerializable {
	private String id;
	@JsonIgnore
	private long entityVersion;

	protected Entity() {
		this.id = UUID.randomUUID().toString();
	}

	protected Entity(String id) {
		this.id = id;
	}

	protected Entity(Entity entity) {
		this.id = entity.getId();
		this.entityVersion = entity.getEntityVersion();
	}

	public boolean equals(Object target) {
		if (this == target) {
			return true;
		} else if (target != null && this.getClass() == target.getClass()) {
			Entity entity = (Entity)target;
			return Objects.equals(this.id, entity.id);
		} else {
			return false;
		}
	}

	public int hashCode() {
		return Objects.hash(this.id);
	}

	public String toString() {
		return this.toJson();
	}
}
