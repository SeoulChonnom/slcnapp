package com.seoulchonnom.auth.flow;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.user.logic.UserLogic;
import com.seoulchonnom.auth.flow.vo.UserSessionVo;
import com.seoulchonnom.auth.logic.UserAuthLogic;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFlow {
	private final UserLogic userLogic;
	private final UserAuthLogic userAuthLogic;
	private final UserMapper userMapper;

	public void registerUser(UserCdo userCdo) {
		userLogic.registerUser(userCdo);
	}

	public UserSessionVo login(UserLoginCdo userLoginCdo) {
		TokenRdo tokenRdo = userAuthLogic.issueLoginToken(userLoginCdo);
		User user = userLogic.getUser(tokenRdo);
		return UserSessionVo.builder()
			.tokenRdo(tokenRdo)
			.userRdo(userMapper.toUserRdo(user, tokenRdo.getAccessToken()))
			.build();
	}

	public UserSessionVo reissue(String refreshToken) {
		TokenRdo tokenRdo = userAuthLogic.reissueToken(refreshToken);
		User user = userLogic.getUser(tokenRdo);
		return UserSessionVo.builder()
			.tokenRdo(tokenRdo)
			.userRdo(userMapper.toUserRdo(user, tokenRdo.getAccessToken()))
			.build();
	}
}
