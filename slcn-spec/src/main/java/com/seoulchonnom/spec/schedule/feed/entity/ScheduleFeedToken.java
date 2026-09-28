package com.seoulchonnom.spec.schedule.feed.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ScheduleFeedToken extends DomainEntity {
	private String name;
	@JsonIgnore
	private String tokenHash;
}
