package com.seoulchonnom.aggregate.user.store;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;
import com.seoulchonnom.aggregate.user.store.mapper.UserJpoMapper;
import com.seoulchonnom.aggregate.user.store.repository.UserRepository;
import com.seoulchonnom.spec.user.entity.User;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStore {
	private final UserRepository userRepository;
	private final UserJpoMapper userJpoMapper;

	public void save(User user) {
		userRepository.save(userJpoMapper.toJpo(user));
	}

	public User findUserById(String id) {
		UserJpo userJpo = userRepository.findUserJpoById(id).orElseThrow(InvalidUserException::new);
		return userJpoMapper.toDomain(userJpo);
	}

	public User findUserByUserName(String name) {
		return userJpoMapper.toDomain(
			userRepository.findUserJpoByUsername(name).orElseThrow(InvalidUserException::new));
	}

}
