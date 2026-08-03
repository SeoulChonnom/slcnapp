package com.seoulchonnom.spec.client.facade.sdo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientTokenCdo {
	@JsonProperty("client_id")
	@JsonAlias("clientId")
	private String clientId;

	@JsonProperty("client_secret")
	@JsonAlias("clientSecret")
	private String clientSecret;
}
