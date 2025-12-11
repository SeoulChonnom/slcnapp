package com.seoulchonnom.spec.common.entity;

import java.time.LocalDateTime;

public abstract class DomainEntity extends Entity {

	protected LocalDateTime registeredTime;
	protected LocalDateTime modifiedTime;

	protected DomainEntity(String id) {
		super(id);
		registeredTime = LocalDateTime.now();
		modifiedTime = LocalDateTime.now();
	}

	protected DomainEntity() {
		super();
		registeredTime = LocalDateTime.now();
		modifiedTime = LocalDateTime.now();
	}
}