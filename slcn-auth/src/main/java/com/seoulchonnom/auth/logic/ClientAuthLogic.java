package com.seoulchonnom.auth.logic;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.client.logic.ClientLogic;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.spec.client.entity.Client;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientAuthLogic {
	private final ClientLogic clientLogic;
	private final JwtTokenProvider jwtTokenProvider;

	public String issueAccessToken(ClientTokenCdo clientTokenCdo) {
		String clientId = clientTokenCdo != null ? clientTokenCdo.getClientId() : null;
		String clientSecret = clientTokenCdo != null ? clientTokenCdo.getClientSecret() : null;
		Client client = clientLogic.authenticate(clientId, clientSecret);
		return jwtTokenProvider.createClientAccessToken(client.getClientId(), client.getClientName());
	}
}
