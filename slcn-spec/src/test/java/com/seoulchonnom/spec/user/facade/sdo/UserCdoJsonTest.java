package com.seoulchonnom.spec.user.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class UserCdoJsonTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldDeserializeUsernameField() throws Exception {
		UserCdo userCdo = objectMapper.readValue("""
			{
			  "name": "tester",
			  "username": "tester-id",
			  "password": "secret"
			}
			""", UserCdo.class);

		assertThat(userCdo.getUsername()).isEqualTo("tester-id");
	}

	@Test
	void shouldDeserializeLegacyUserNameField() throws Exception {
		UserCdo userCdo = objectMapper.readValue("""
			{
			  "name": "tester",
			  "userName": "legacy-id",
			  "password": "secret"
			}
			""", UserCdo.class);

		assertThat(userCdo.getUsername()).isEqualTo("legacy-id");
	}
}
