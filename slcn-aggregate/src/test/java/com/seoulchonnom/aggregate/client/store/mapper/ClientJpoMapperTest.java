package com.seoulchonnom.aggregate.client.store.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.client.store.jpo.ClientJpo;
import com.seoulchonnom.spec.client.entity.Client;

class ClientJpoMapperTest {
	private final ClientJpoMapper clientJpoMapper = new ClientJpoMapper();

	@Test
	void toDomain_shouldPreserveClientAndInheritedFields() {
		ClientJpo clientJpo = new ClientJpo("batch-client", "plain-secret", "Batch client", "cronjob client");
		clientJpo.setId("CLIENT-0001");
		clientJpo.setEntityVersion(3L);
		clientJpo.setRegisteredTime(100L);
		clientJpo.setModifiedTime(200L);

		Client client = clientJpoMapper.toDomain(clientJpo);

		assertThat(client.getId()).isEqualTo("CLIENT-0001");
		assertThat(client.getEntityVersion()).isEqualTo(3L);
		assertThat(client.getRegisteredTime()).isEqualTo(100L);
		assertThat(client.getModifiedTime()).isEqualTo(200L);
		assertThat(client.getClientId()).isEqualTo("batch-client");
		assertThat(client.getClientSecret()).isEqualTo("plain-secret");
		assertThat(client.getClientName()).isEqualTo("Batch client");
		assertThat(client.getClientDescription()).isEqualTo("cronjob client");
	}

	@Test
	void toJpo_shouldPreserveClientAndInheritedFields() {
		Client client = Client.builder()
			.clientId("batch-client")
			.clientSecret("plain-secret")
			.clientName("Batch client")
			.clientDescription("cronjob client")
			.build();
		client.setId("CLIENT-0001");
		client.setEntityVersion(3L);
		client.setRegisteredTime(100L);
		client.setModifiedTime(200L);

		ClientJpo clientJpo = clientJpoMapper.toJpo(client);

		assertThat(clientJpo.getId()).isEqualTo("CLIENT-0001");
		assertThat(clientJpo.getEntityVersion()).isEqualTo(3L);
		assertThat(clientJpo.getRegisteredTime()).isEqualTo(100L);
		assertThat(clientJpo.getModifiedTime()).isEqualTo(200L);
		assertThat(clientJpo.getClientId()).isEqualTo("batch-client");
		assertThat(clientJpo.getClientSecret()).isEqualTo("plain-secret");
		assertThat(clientJpo.getClientName()).isEqualTo("Batch client");
		assertThat(clientJpo.getClientDescription()).isEqualTo("cronjob client");
	}
}
