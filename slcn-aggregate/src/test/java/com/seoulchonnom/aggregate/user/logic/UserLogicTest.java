package com.seoulchonnom.aggregate.user.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

class UserLogicTest {
	private final PasswordGenerator passwordGenerator = mock(PasswordGenerator.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final UserStore userStore = mock(UserStore.class);
	private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
	private final UserLogic userLogic = new UserLogic(passwordGenerator, idGenerator, userStore, userMapper);

	@Test
	void registerUser_shouldCreateInitialUserLoginDocument() {
		UserCdo userCdo = UserCdo.builder()
			.name("tester")
			.username("tester")
			.password("Password1!")
			.build();
		when(idGenerator.nextDomainId("USER")).thenReturn("USER-0001");
		when(passwordGenerator.encode("Password1!")).thenReturn("encoded-password");
		when(userStore.existsByUsername("tester")).thenReturn(false);

		userLogic.registerUser(userCdo);

		verify(userStore).save(any());
		verify(userStore).initializeUserLogin("USER-0001");
	}

	@Test
	void registerUser_shouldRejectDuplicateUsername() {
		UserCdo userCdo = UserCdo.builder()
			.name("tester")
			.username("tester")
			.password("Password1!")
			.build();
		when(userStore.existsByUsername("tester")).thenReturn(true);

		assertThatThrownBy(() -> userLogic.registerUser(userCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("이미 사용 중인 username입니다.");

		verify(idGenerator, never()).nextDomainId(anyString());
		verify(userStore, never()).save(any());
	}
}
