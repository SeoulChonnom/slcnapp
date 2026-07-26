package com.seoulchonnom.aggregate.user.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.PasswordGenerator;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.user.store.UserStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

class UserLogicTest {
	private final PasswordGenerator passwordGenerator = mock(PasswordGenerator.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final UserStore userStore = mock(UserStore.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
	private final UserLogic userLogic = new UserLogic(passwordGenerator, idGenerator, userStore, fileAssetStore, userMapper);

	@Test
	void registerUser_shouldCreateInitialUserLoginDocument() {
		UserCdo userCdo = UserCdo.builder()
			.name("tester")
			.username("tester")
			.password("Password1!")
			.build();
		when(idGenerator.nextDomainId("USER")).thenReturn("USER-0001");
		when(passwordGenerator.encode("Password1!")).thenReturn("encoded-password");
		when(userStore.existsByUsername("tester")).thenReturn(false);

		userLogic.registerUser(userCdo);

		verify(userStore).save(any());
		verify(userStore).initializeUserLogin("USER-0001");
	}

	@Test
	void registerUser_shouldRejectDuplicateUsername() {
		UserCdo userCdo = UserCdo.builder()
			.name("tester")
			.username("tester")
			.password("Password1!")
			.build();
		when(userStore.existsByUsername("tester")).thenReturn(true);

		assertThatThrownBy(() -> userLogic.registerUser(userCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("이미 사용 중인 username입니다.");

		verify(idGenerator, never()).nextDomainId(anyString());
		verify(userStore, never()).save(any());
	}

	@Test
	void updateUserProfile_shouldSaveProfileAssetReferenceWithoutPasswordVerification() {
		User user = User.builder()
			.username("tester")
			.name("before")
			.password("encoded-current")
			.credentialVersion(4L)
			.build();
		user.setId("USER-0001");
		UserProfileUdo userProfileUdo = new UserProfileUdo();
		userProfileUdo.setName(" after ");
		userProfileUdo.setNewPassword("NewPassword1!");
		userProfileUdo.setProfileImageFileId("file-1");
		FileAsset profileImage = new FileAsset(FileType.PROFILE, "profile.png", "stored.png", "image/png", 1L);
		profileImage.setId("file-1");
		when(userStore.findUserById("USER-0001")).thenReturn(user);
		when(passwordGenerator.encode("NewPassword1!")).thenReturn("encoded-new");
		when(fileAssetStore.findById("file-1")).thenReturn(profileImage);

		User result = userLogic.updateUserProfile("USER-0001", userProfileUdo);

		assertThat(result.getName()).isEqualTo("after");
		assertThat(result.getPassword()).isEqualTo("encoded-new");
		assertThat(result.getCredentialVersion()).isEqualTo(5L);
		assertThat(result.getProfileImageFileId()).isEqualTo("file-1");
		verify(userStore).save(user);
		verify(passwordGenerator, never()).matches(anyString(), anyString());
	}

	@Test
	void updateUserProfile_shouldRejectNonProfileAsset() {
		User user = User.builder().password("encoded-current").build();
		UserProfileUdo userProfileUdo = new UserProfileUdo();
		userProfileUdo.setProfileImageFileId("file-1");
		FileAsset travelImage = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		when(userStore.findUserById("USER-0001")).thenReturn(user);
		when(fileAssetStore.findById("file-1")).thenReturn(travelImage);

		assertThatThrownBy(() -> userLogic.updateUserProfile("USER-0001", userProfileUdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("프로필 이미지 파일 타입이 올바르지 않습니다.");

		verify(userStore, never()).save(any());
	}

	@Test
	void verifyPassword_shouldRejectIncorrectPassword() {
		User user = User.builder().password("encoded-current").build();
		UserPasswordVerifyCdo userPasswordVerifyCdo = new UserPasswordVerifyCdo();
		userPasswordVerifyCdo.setPassword("incorrect");
		when(userStore.findUserById("USER-0001")).thenReturn(user);
		when(passwordGenerator.matches("incorrect", "encoded-current")).thenReturn(false);

		assertThatThrownBy(() -> userLogic.verifyPassword("USER-0001", userPasswordVerifyCdo))
			.isInstanceOf(com.seoulchonnom.aggregate.user.exception.InvalidUserException.class);
	}
}
