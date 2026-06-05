package com.seoulchonnom.auth.logic;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.exception.UserLoginFailCountOverException;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.store.RefreshSessionStore;
import com.seoulchonnom.auth.store.UserAuthStore;
import com.seoulchonnom.auth.store.projection.RefreshSession;
import com.seoulchonnom.auth.store.projection.UserDetail;
import com.seoulchonnom.auth.util.JwtTokenProvider;
import com.seoulchonnom.auth.util.JwtTokenProvider.TokenValidationResult;
import com.seoulchonnom.auth.util.RefreshTokenHasher;
import com.seoulchonnom.spec.user.entity.UserLogin;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAuthLogic {

	private final UserAuthStore userAuthStore;
	private final RefreshSessionStore refreshSessionStore;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenHasher refreshTokenHasher;
	private final PasswordGenerator passwordGenerator;

	@Value("${login.fail.limit.count:5}")
	private int loginFailLimitCount;

	public TokenSessionVo issueLoginToken(UserLoginCdo userLoginCdo) {
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
		String sessionId = UUID.randomUUID().toString();
		saveRefreshSession(sessionId, tokenRdo);
		recordLoginSuccess(userLogin);
		return new TokenSessionVo(sessionId, tokenRdo);
	}

	public TokenSessionVo reissueToken(String refreshToken, String sessionId) {
		TokenValidationResult validationResult = jwtTokenProvider.validateRefreshToken(refreshToken);
		if (!validationResult.valid() || !StringUtils.hasText(sessionId)) {
			throw new InvalidRefreshTokenException();
		}

		RefreshSession refreshSession = refreshSessionStore.findBySessionId(sessionId)
			.orElseThrow(InvalidRefreshTokenException::new);
		String userId = validationResult.claims().getSubject();
		if (!userId.equals(refreshSession.userId())) {
			throw new InvalidRefreshTokenException();
		}

		String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
		if (!refreshTokenHash.equals(refreshSession.refreshTokenHash())) {
			throw new InvalidRefreshTokenException();
		}

		UserDetail userDetail = userAuthStore.getUserDetailById(validationResult.claims().getSubject());
		TokenRdo tokenRdo = jwtTokenProvider.createToken(userDetail, userDetail.getUser().getId());
		saveRefreshSession(sessionId, tokenRdo);
		return new TokenSessionVo(sessionId, tokenRdo);
	}

	public void logout(String sessionId) {
		if (StringUtils.hasText(sessionId)) {
			refreshSessionStore.delete(sessionId);
		}
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

	private void saveRefreshSession(String sessionId, TokenRdo tokenRdo) {
		Duration ttl = jwtTokenProvider.getRefreshTokenTtl();
		Instant now = Instant.now();
		refreshSessionStore.save(new RefreshSession(
			sessionId,
			tokenRdo.getUserId(),
			refreshTokenHasher.hash(tokenRdo.getRefreshToken()),
			now.toEpochMilli(),
			now.plus(ttl).toEpochMilli()
		), ttl);
	}
}
