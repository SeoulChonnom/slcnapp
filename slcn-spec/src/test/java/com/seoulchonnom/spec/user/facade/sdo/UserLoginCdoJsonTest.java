package com.seoulchonnom.spec.user.facade.sdo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class UserLoginCdoJsonTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldDeserializeUsernameField() throws Exception {
		UserLoginCdo userLoginCdo = objectMapper.readValue("""
			{
			  "username": "tester-id",
			  "password": "secret"
			}
			""", UserLoginCdo.class);

		assertThat(userLoginCdo.getUsername()).isEqualTo("tester-id");
	}

	@Test
	void shouldDeserializeLegacyUserNameField() throws Exception {
		UserLoginCdo userLoginCdo = objectMapper.readValue("""
			{
			  "userName": "legacy-id",
			  "password": "secret"
			}
			""", UserLoginCdo.class);

		assertThat(userLoginCdo.getUsername()).isEqualTo("legacy-id");
	}
}
