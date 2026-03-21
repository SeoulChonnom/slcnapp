package com.seoulchonnom.aggregate.common.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@MappedSuperclass
@NoArgsConstructor
@Getter
@Setter
public abstract class DomainEntityJpo extends EntityJpo {
	protected Long registeredTime;
	protected Long modifiedTime;
}
