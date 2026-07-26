package com.seoulchonnom.auth.flow;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.user.logic.UserLogic;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.flow.vo.UserProfileSessionVo;
import com.seoulchonnom.auth.flow.vo.UserSessionVo;
import com.seoulchonnom.auth.logic.UserAuthLogic;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFlow {
	private final UserLogic userLogic;
	private final UserAuthLogic userAuthLogic;
	private final FileAssetStore fileAssetStore;
	private final UserMapper userMapper;

	public void registerUser(UserCdo userCdo) {
		userLogic.registerUser(userCdo);
	}

	public UserSessionVo login(UserLoginCdo userLoginCdo) {
		TokenSessionVo tokenSessionVo = userAuthLogic.issueLoginToken(userLoginCdo);
		TokenRdo tokenRdo = tokenSessionVo.tokenRdo();
		User user = userLogic.getUser(tokenRdo);
		return UserSessionVo.builder()
			.sessionId(tokenSessionVo.sessionId())
			.tokenRdo(tokenRdo)
			.userRdo(userMapper.toUserRdo(user, tokenRdo.getAccessToken()))
			.build();
	}

	public UserSessionVo reissue(String refreshToken, String sessionId) {
		TokenSessionVo tokenSessionVo = userAuthLogic.reissueToken(refreshToken, sessionId);
		TokenRdo tokenRdo = tokenSessionVo.tokenRdo();
		User user = userLogic.getUser(tokenRdo);
		return UserSessionVo.builder()
			.sessionId(tokenSessionVo.sessionId())
			.tokenRdo(tokenRdo)
			.userRdo(userMapper.toUserRdo(user, tokenRdo.getAccessToken()))
			.build();
	}

	public void logout(String sessionId) {
		userAuthLogic.logout(sessionId);
	}

	public UserProfileRdo getCurrentUser(String userId) {
		return toUserProfileRdo(userLogic.getUserProfile(userId));
	}

	public void verifyCurrentUserPassword(String userId, UserPasswordVerifyCdo userPasswordVerifyCdo) {
		userLogic.verifyPassword(userId, userPasswordVerifyCdo);
	}

	public UserProfileSessionVo updateCurrentUser(String userId, UserProfileUdo userProfileUdo) {
		User user = userLogic.updateUserProfile(userId, userProfileUdo);
		TokenSessionVo tokenSessionVo = null;
		if (StringUtils.hasText(userProfileUdo.getNewPassword())) {
			userAuthLogic.logoutAll(userId);
			tokenSessionVo = userAuthLogic.issueAuthenticatedUserRefreshSession(userId);
		}
		return new UserProfileSessionVo(toUserProfileRdo(user), tokenSessionVo);
	}

	private UserProfileRdo toUserProfileRdo(User user) {
		FileAssetRdo profileImage = null;
		if (user.getProfileImageFileId() != null) {
			FileAsset fileAsset = fileAssetStore.findById(user.getProfileImageFileId());
			profileImage = FileAssetRdo.from(fileAsset);
		}
		return userMapper.toUserProfileRdo(user, profileImage);
	}
}
