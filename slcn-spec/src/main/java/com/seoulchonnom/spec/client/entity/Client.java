package com.seoulchonnom.spec.client.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Client extends DomainEntity {
	private String clientId;
	private String clientSecret;
	private String clientName;
	private String clientDescription;
}
