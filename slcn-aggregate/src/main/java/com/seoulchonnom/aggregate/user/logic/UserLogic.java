package com.seoulchonnom.aggregate.user.logic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.user.exception.InvalidUserException;
import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLogic {
	private final PasswordGenerator passwordGenerator;
	private final IdGenerator idGenerator;
	private final UserStore userStore;
	private final FileAssetStore fileAssetStore;
	private final UserMapper userMapper;

	private final String LOGIN_ERROR_CODE = "ERROR";

	@Transactional
	public void registerUser(UserCdo userCdo) {
		if (!StringUtils.hasText(userCdo.getUsername())) {
			throw new BadRequestException("username은 필수입니다.");
		}

		if (userStore.existsByUsername(userCdo.getUsername())) {
			throw new BadRequestException("이미 사용 중인 username입니다.");
		}

		String userId = idGenerator.nextDomainId(SequenceName.USER.toString());
		User user = userMapper.toUser(userCdo, userId, passwordGenerator.encode(userCdo.getPassword()));

		userStore.save(user);
		userStore.initializeUserLogin(userId);
	}

	public User getUser(TokenRdo tokenRdo) {
		if (tokenRdo.getUserId().equals(LOGIN_ERROR_CODE)) {
			throw new InvalidUserException();
		}

		return userStore.findUserById(tokenRdo.getUserId());
	}

	public User getUserProfile(String userId) {
		return userStore.findUserById(userId);
	}

	public void verifyPassword(String userId, UserPasswordVerifyCdo userPasswordVerifyCdo) {
		User user = userStore.findUserById(userId);
		verifyPassword(user, userPasswordVerifyCdo.getPassword());
	}

	@Transactional
	public User updateUserProfile(String userId, UserProfileUdo userProfileUdo) {
		User user = userStore.findUserById(userId);

		if (userProfileUdo.getName() != null) {
			if (!StringUtils.hasText(userProfileUdo.getName())) {
				throw new BadRequestException("name은 빈 값일 수 없습니다.");
			}
			user.setName(userProfileUdo.getName().trim());
		}

		if (userProfileUdo.getNewPassword() != null) {
			if (!StringUtils.hasText(userProfileUdo.getNewPassword())) {
				throw new BadRequestException("newPassword는 빈 값일 수 없습니다.");
			}
			user.setPassword(passwordGenerator.encode(userProfileUdo.getNewPassword()));
			user.setCredentialVersion(user.getCredentialVersion() + 1);
		}

		if (userProfileUdo.getProfileImageFileId() != null) {
			if (!StringUtils.hasText(userProfileUdo.getProfileImageFileId())) {
				user.setProfileImageFileId(null);
			} else {
				FileAsset profileImage = fileAssetStore.findById(userProfileUdo.getProfileImageFileId().trim());
				if (profileImage.getType() != FileType.PROFILE) {
					throw new BadRequestException("프로필 이미지 파일 타입이 올바르지 않습니다.");
				}
				user.setProfileImageFileId(profileImage.getId());
			}
		}

		userStore.save(user);
		return user;
	}

	private void verifyPassword(User user, String currentPassword) {
		if (!StringUtils.hasText(currentPassword) || !passwordGenerator.matches(currentPassword, user.getPassword())) {
			throw new InvalidUserException();
		}
	}
}
