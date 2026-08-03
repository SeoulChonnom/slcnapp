package com.seoulchonnom.aggregate.client.logic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.client.exception.InvalidClientException;
import com.seoulchonnom.aggregate.client.store.ClientStore;
import com.seoulchonnom.spec.client.entity.Client;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientLogic {
	private final ClientStore clientStore;

	public Client findClientByClientId(String clientId) {
		return clientStore.findByClientId(clientId);
	}

	public Client authenticate(String clientId, String clientSecret) {
		if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
			throw new InvalidClientException();
		}

		Client client = findClientByClientId(clientId);
		if (!clientSecret.equals(client.getClientSecret())) {
			throw new InvalidClientException();
		}
		return client;
	}
}
