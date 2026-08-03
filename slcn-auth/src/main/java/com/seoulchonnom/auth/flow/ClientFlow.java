package com.seoulchonnom.auth.flow;

import org.springframework.stereotype.Component;

import com.seoulchonnom.auth.logic.ClientAuthLogic;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenRdo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClientFlow {
	private final ClientAuthLogic clientAuthLogic;

	public ClientTokenRdo issueToken(ClientTokenCdo clientTokenCdo) {
		return ClientTokenRdo.builder()
			.accessToken(clientAuthLogic.issueAccessToken(clientTokenCdo))
			.build();
	}
}
