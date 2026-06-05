package com.seoulchonnom.aggregate.common.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@MappedSuperclass
@NoArgsConstructor
@Getter
@Setter
public abstract class EntityJpo implements Serializable {
	@Id
	protected String id;
	@Version
	protected long entityVersion;

	protected EntityJpo(String id) {
		this.id = id;
		this.entityVersion = 0L;
	}

	protected EntityJpo(EntityJpo entityJpo) {
		this.id = entityJpo.getId();
		this.entityVersion = entityJpo.getEntityVersion();
	}

	public boolean equals(Object target) {
		if (this == target) {
			return true;
		} else if (target != null && this.getClass() == target.getClass()) {
			EntityJpo entity = (EntityJpo)target;
			return Objects.equals(this.id, entity.id);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		//
		return Objects.hash(id);
	}

}
