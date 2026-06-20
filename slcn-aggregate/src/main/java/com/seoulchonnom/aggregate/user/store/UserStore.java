package com.seoulchonnom.aggregate.user.store;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.exception.UserLoginNotFoundException;
import com.seoulchonnom.aggregate.user.store.doc.UserLoginDoc;
import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;
import com.seoulchonnom.aggregate.user.store.mapper.UserJpoMapper;
import com.seoulchonnom.aggregate.user.store.mapper.UserLoginDocMapper;
import com.seoulchonnom.aggregate.user.store.mapper.UserLoginHistoryDocMapper;
import com.seoulchonnom.aggregate.user.store.repository.UserLoginHistoryRepository;
import com.seoulchonnom.aggregate.user.store.repository.UserLoginRepository;
import com.seoulchonnom.aggregate.user.store.repository.UserRepository;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.entity.UserLogin;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStore {
	private final UserRepository userRepository;
	private final UserJpoMapper userJpoMapper;
	private final UserLoginRepository userLoginRepository;
	private final UserLoginHistoryRepository userLoginHistoryRepository;
	private final UserLoginDocMapper userLoginDocMapper;
	private final UserLoginHistoryDocMapper userLoginHistoryDocMapper;

	public void save(User user) {
		userRepository.save(userJpoMapper.toJpo(user));
	}

	public void initializeUserLogin(String userId) {
		userLoginRepository.save(userLoginDocMapper.toDoc(UserLogin.newUser(userId)));
	}

	public boolean existsByUsername(String username) {
		return userRepository.existsByUsername(username);
	}

	public User findUserById(String id) {
		UserJpo userJpo = userRepository.findUserJpoById(id).orElseThrow(InvalidUserException::new);
		return userJpoMapper.toDomain(userJpo);
	}

	public User findUserByUserName(String name) {
		return userJpoMapper.toDomain(
			userRepository.findUserJpoByUsername(name).orElseThrow(InvalidUserException::new));
	}

	public UserLogin findUserLoginByUserId(String userId) {
		UserLoginDoc userLoginDoc = userLoginRepository.findById(userId)
			.orElseThrow(UserLoginNotFoundException::new);
		return userLoginDocMapper.toDomain(userLoginDoc);
	}

	public void saveUserLogin(UserLogin userLogin) {
		userLoginRepository.save(userLoginDocMapper.toDoc(userLogin));
	}

	public void saveUserLoginHistory(UserLoginHistory userLoginHistory) {
		userLoginHistoryRepository.save(userLoginHistoryDocMapper.toDoc(userLoginHistory));
	}
}
