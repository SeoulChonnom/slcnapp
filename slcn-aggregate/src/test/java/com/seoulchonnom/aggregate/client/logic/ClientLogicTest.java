package com.seoulchonnom.aggregate.client.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.client.exception.InvalidClientException;
import com.seoulchonnom.aggregate.client.store.ClientStore;
import com.seoulchonnom.spec.client.entity.Client;

class ClientLogicTest {
	private final ClientStore clientStore = mock(ClientStore.class);
	private final ClientLogic clientLogic = new ClientLogic(clientStore);

	@Test
	void authenticate_shouldReturnClientWhenPlainTextCredentialMatches() {
		Client client = Client.builder().clientId("batch-client").clientSecret("plain-secret").build();
		when(clientStore.findByClientId("batch-client")).thenReturn(client);

		Client result = clientLogic.authenticate("batch-client", "plain-secret");

		assertThat(result).isSameAs(client);
		verify(clientStore).findByClientId("batch-client");
	}

	@Test
	void authenticate_shouldRejectDifferentSecret() {
		Client client = Client.builder().clientId("batch-client").clientSecret("plain-secret").build();
		when(clientStore.findByClientId("batch-client")).thenReturn(client);

		assertThatThrownBy(() -> clientLogic.authenticate("batch-client", "wrong-secret"))
			.isInstanceOf(InvalidClientException.class);
	}

	@Test
	void authenticate_shouldRejectBlankCredentialWithoutLookup() {
		assertThatThrownBy(() -> clientLogic.authenticate("batch-client", " "))
			.isInstanceOf(InvalidClientException.class);

		verifyNoInteractions(clientStore);
	}
}
