package com.seoulchonnom.rest.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.auth.flow.ClientFlow;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenCdo;
import com.seoulchonnom.spec.client.facade.sdo.ClientTokenRdo;

class ClientResourceJsonContractTest {
	private ClientFlow clientFlow;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		clientFlow = mock(ClientFlow.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new ClientResource(clientFlow)).build();
	}

	@Test
	void issueClientToken_shouldBindClientCredentialsAndReturnAccessToken() throws Exception {
		when(clientFlow.issueToken(any(ClientTokenCdo.class)))
			.thenReturn(ClientTokenRdo.builder().accessToken("client-access-token").build());

		mockMvc.perform(post("/clients/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "client_id": "cron-service",
					  "client_secret": "client-secret"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("client-access-token"));

		ArgumentCaptor<ClientTokenCdo> captor = ArgumentCaptor.forClass(ClientTokenCdo.class);
		verify(clientFlow).issueToken(captor.capture());
		assertEquals("cron-service", captor.getValue().getClientId());
		assertEquals("client-secret", captor.getValue().getClientSecret());
	}
}
