package com.seoulchonnom.auth.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.client.exception.InvalidClientException;
import com.seoulchonnom.aggregate.client.logic.ClientLogic;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.spec.client.entity.Client;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;

class ClientAuthLogicTest {
	private ClientLogic clientLogic;
	private JwtTokenProvider jwtTokenProvider;
	private ClientAuthLogic clientAuthLogic;

	@BeforeEach
	void setUp() {
		clientLogic = mock(ClientLogic.class);
		jwtTokenProvider = mock(JwtTokenProvider.class);
		clientAuthLogic = new ClientAuthLogic(clientLogic, jwtTokenProvider);
	}

	@Test
	void issueAccessToken_shouldAuthenticateClientAndCreateAccessToken() {
		ClientTokenCdo clientTokenCdo = ClientTokenCdo.builder()
			.clientId("CRON-001")
			.clientSecret("plain-secret")
			.build();
		Client client = Client.builder().clientId("CRON-001").clientName("schedule-cron").build();
		when(clientLogic.authenticate("CRON-001", "plain-secret")).thenReturn(client);
		when(jwtTokenProvider.createClientAccessToken("CRON-001", "schedule-cron")).thenReturn("client-access-token");

		String accessToken = clientAuthLogic.issueAccessToken(clientTokenCdo);

		assertThat(accessToken).isEqualTo("client-access-token");
		verify(clientLogic).authenticate("CRON-001", "plain-secret");
		verify(jwtTokenProvider).createClientAccessToken("CRON-001", "schedule-cron");
	}

	@Test
	void issueAccessToken_invalidCredentials_shouldNotCreateToken() {
		ClientTokenCdo clientTokenCdo = ClientTokenCdo.builder()
			.clientId("CRON-001")
			.clientSecret("invalid-secret")
			.build();
		when(clientLogic.authenticate("CRON-001", "invalid-secret")).thenThrow(new InvalidClientException());

		assertThatThrownBy(() -> clientAuthLogic.issueAccessToken(clientTokenCdo))
			.isInstanceOf(InvalidClientException.class);

		verifyNoInteractions(jwtTokenProvider);
	}
}
