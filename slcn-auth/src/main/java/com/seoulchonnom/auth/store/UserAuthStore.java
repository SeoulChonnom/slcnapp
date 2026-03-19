package com.seoulchonnom.auth.store;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.auth.store.projection.UserDetail;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserAuthStore {
	private final UserStore userStore;

	public UserDetail getUserDetail(String name) {
		return new UserDetail(userStore.findUserByUserName(name));
	}
}
