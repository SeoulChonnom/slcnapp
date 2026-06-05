package com.seoulchonnom.aggregate.user.logic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLogic {
	private final PasswordGenerator passwordGenerator;
	private final IdGenerator idGenerator;
	private final UserStore userStore;

	private final String LOGIN_ERROR_CODE = "ERROR";

	@Transactional
	public void registerUser(UserCdo userCdo) {
		String userId = idGenerator.nextDomainId(SequenceName.USER.toString());
		User user = new User(userCdo, userId, passwordGenerator.encode(userCdo.getPassword()));

		userStore.save(user);
		userStore.initializeUserLogin(userId);
	}

	public User getUser(TokenRdo tokenRdo) {
		if (tokenRdo.getUserId().equals(LOGIN_ERROR_CODE)) {
			throw new InvalidUserException();
		}

		return userStore.findUserById(tokenRdo.getUserId());
	}
}
