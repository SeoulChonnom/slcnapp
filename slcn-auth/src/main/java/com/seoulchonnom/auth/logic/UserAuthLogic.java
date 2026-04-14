package com.seoulchonnom.auth.logic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.exception.UserLoginFailCountOverException;
import com.seoulchonnom.auth.store.UserAuthStore;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;
import com.seoulchonnom.spec.user.entity.UserLogin;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAuthLogic {

	private final UserAuthStore userAuthStore;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordGenerator passwordGenerator;

	@Value("${login.fail.limit.count:5}")
	private int loginFailLimitCount;

	public TokenRdo issueLoginToken(UserLoginCdo userLoginCdo) {
		UserDetail userDetail = userAuthStore.getUserDetail(userLoginCdo.getUsername());
		UserLogin userLogin = userAuthStore.getUserLogin(userDetail.getUser().getId());

		if (userLogin.isLoginBlocked(loginFailLimitCount)) {
			recordLoginFailure(userLogin);
			throw new UserLoginFailCountOverException();
		}

		if (!passwordGenerator.matches(userLoginCdo.getPassword(), userDetail.getPassword())) {
			recordLoginFailure(userLogin);
			throw new InvalidUserException();
		}

		TokenRdo tokenRdo = jwtTokenProvider.createToken(userDetail, userDetail.getUser().getId());
		recordLoginSuccess(userLogin);
		return tokenRdo;
	}

	public TokenRdo reissueToken(String refreshToken) {
		TokenValidationResult validationResult = jwtTokenProvider.validateRefreshToken(refreshToken);
		if (!validationResult.valid()) {
			throw new InvalidRefreshTokenException();
		}

		UserDetail userDetail = userAuthStore.getUserDetailById(validationResult.claims().getSubject());
		return jwtTokenProvider.createToken(userDetail, userDetail.getUser().getId());
	}

	private void recordLoginFailure(UserLogin userLogin) {
		userLogin.markLoginFailure();
		userAuthStore.saveUserLogin(userLogin);
		userAuthStore.saveUserLoginHistory(UserLoginHistory.create(userLogin.getUserId(), false));
	}

	private void recordLoginSuccess(UserLogin userLogin) {
		userLogin.markLoginSuccess();
		userAuthStore.saveUserLogin(userLogin);
		userAuthStore.saveUserLoginHistory(UserLoginHistory.create(userLogin.getUserId(), true));
	}
}
