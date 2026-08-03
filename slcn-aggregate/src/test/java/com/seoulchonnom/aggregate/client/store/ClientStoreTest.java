package com.seoulchonnom.aggregate.client.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.client.exception.InvalidClientException;
import com.seoulchonnom.aggregate.client.store.jpo.ClientJpo;
import com.seoulchonnom.aggregate.client.store.mapper.ClientJpoMapper;
import com.seoulchonnom.aggregate.client.store.repository.ClientRepository;
import com.seoulchonnom.spec.client.entity.Client;

class ClientStoreTest {
	private final ClientRepository clientRepository = mock(ClientRepository.class);
	private final ClientJpoMapper clientJpoMapper = new ClientJpoMapper();
	private final ClientStore clientStore = new ClientStore(clientRepository, clientJpoMapper);

	@Test
	void findByClientId_shouldMapFoundClient() {
		ClientJpo clientJpo = new ClientJpo("batch-client", "plain-secret", "Batch client", "cronjob client");
		clientJpo.setId("CLIENT-0001");
		when(clientRepository.findByClientId("batch-client")).thenReturn(Optional.of(clientJpo));

		Client client = clientStore.findByClientId("batch-client");

		assertThat(client.getId()).isEqualTo("CLIENT-0001");
		assertThat(client.getClientSecret()).isEqualTo("plain-secret");
		verify(clientRepository).findByClientId("batch-client");
	}

	@Test
	void findByClientId_shouldRejectUnknownClient() {
		when(clientRepository.findByClientId("unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> clientStore.findByClientId("unknown"))
			.isInstanceOf(InvalidClientException.class);
	}
}
