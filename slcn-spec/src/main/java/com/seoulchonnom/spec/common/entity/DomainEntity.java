package com.seoulchonnom.spec.common.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class DomainEntity extends Entity {

	protected long registeredTime;
	protected long modifiedTime;

	protected DomainEntity(String id) {
		super(id);
		registeredTime = System.currentTimeMillis();
		modifiedTime = System.currentTimeMillis();
	}

	protected DomainEntity() {
		super();
		registeredTime = System.currentTimeMillis();
		modifiedTime = System.currentTimeMillis();
	}
}