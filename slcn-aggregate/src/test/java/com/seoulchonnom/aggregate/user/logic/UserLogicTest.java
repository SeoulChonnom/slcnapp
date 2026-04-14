package com.seoulchonnom.aggregate.user.logic;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;

class UserLogicTest {
	private final PasswordGenerator passwordGenerator = mock(PasswordGenerator.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final UserStore userStore = mock(UserStore.class);
	private final UserLogic userLogic = new UserLogic(passwordGenerator, idGenerator, userStore);

	@Test
	void registerUser_shouldCreateInitialUserLoginDocument() {
		UserCdo userCdo = UserCdo.builder()
			.name("tester")
			.username("tester")
			.password("Password1!")
			.build();
		when(idGenerator.nextDomainId("USER")).thenReturn("USER-0001");
		when(passwordGenerator.encode("Password1!")).thenReturn("encoded-password");

		userLogic.registerUser(userCdo);

		verify(userStore).save(any());
		verify(userStore).initializeUserLogin("USER-0001");
	}
}
