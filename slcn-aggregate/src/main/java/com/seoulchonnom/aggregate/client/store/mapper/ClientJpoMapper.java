package com.seoulchonnom.aggregate.client.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.client.store.jpo.ClientJpo;
import com.seoulchonnom.spec.client.entity.Client;

@Component
public class ClientJpoMapper {
	public ClientJpo toJpo(Client client) {
		ClientJpo clientJpo = new ClientJpo(
			client.getClientId(),
			client.getClientSecret(),
			client.getClientName(),
			client.getClientDescription()
		);
		clientJpo.setId(client.getId());
		clientJpo.setEntityVersion(client.getEntityVersion());
		clientJpo.setRegisteredTime(client.getRegisteredTime());
		clientJpo.setModifiedTime(client.getModifiedTime());
		return clientJpo;
	}

	public Client toDomain(ClientJpo clientJpo) {
		Client client = Client.builder()
			.clientId(clientJpo.getClientId())
			.clientSecret(clientJpo.getClientSecret())
			.clientName(clientJpo.getClientName())
			.clientDescription(clientJpo.getClientDescription())
			.build();
		client.setId(clientJpo.getId());
		client.setEntityVersion(clientJpo.getEntityVersion());
		if (clientJpo.getRegisteredTime() != null) {
			client.setRegisteredTime(clientJpo.getRegisteredTime());
		}
		if (clientJpo.getModifiedTime() != null) {
			client.setModifiedTime(clientJpo.getModifiedTime());
		}
		return client;
	}
}
