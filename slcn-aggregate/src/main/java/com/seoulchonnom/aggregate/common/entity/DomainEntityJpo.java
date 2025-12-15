package com.seoulchonnom.aggregate.common.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class DomainEntityJpo extends EntityJpo {
	@CreationTimestamp
	protected LocalDateTime registeredTime;
	@UpdateTimestamp
	protected LocalDateTime modifiedTime;
}
