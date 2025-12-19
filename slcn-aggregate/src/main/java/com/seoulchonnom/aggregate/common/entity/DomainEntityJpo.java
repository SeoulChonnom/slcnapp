package com.seoulchonnom.aggregate.common.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.NoArgsConstructor;

@MappedSuperclass
@NoArgsConstructor
public abstract class DomainEntityJpo extends EntityJpo {
	protected Long registeredTime;
	protected Long modifiedTime;
}
