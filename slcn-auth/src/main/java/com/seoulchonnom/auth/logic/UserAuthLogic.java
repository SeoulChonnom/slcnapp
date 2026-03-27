package com.seoulchonnom.auth.logic;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.auth.store.UserAuthStore;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAuthLogic {

	private final UserAuthStore userAuthStore;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordGenerator passwordGenerator;

	public TokenRdo issueLoginToken(UserLoginCdo userLoginCdo) {
		UserDetail userDetail = userAuthStore.getUserDetail(userLoginCdo.getUsername());
		if (!passwordGenerator.matches(userLoginCdo.getPassword(), userDetail.getPassword())) {
			throw new InvalidUserException();
		}

		return jwtTokenProvider.createToken(userDetail, userDetail.getUser().getId());
	}

	public TokenRdo reissueToken(String refreshToken) {
		TokenValidationResult validationResult = jwtTokenProvider.validateRefreshToken(refreshToken);
		if (!validationResult.valid()) {
			throw new InvalidRefreshTokenException();
		}

		UserDetail userDetail = userAuthStore.getUserDetailById(validationResult.claims().getSubject());
		return jwtTokenProvider.createToken(userDetail, userDetail.getUser().getId());
	}
}
