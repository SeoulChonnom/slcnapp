package com.seoulchonnom.auth.flow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.aggregate.user.logic.UserLogic;
import com.seoulchonnom.auth.logic.UserAuthLogic;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.flow.vo.UserProfileSessionVo;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
import com.seoulchonnom.spec.user.mapper.UserMapper;

class UserFlowTest {
	private final UserLogic userLogic = mock(UserLogic.class);
	private final UserAuthLogic userAuthLogic = mock(UserAuthLogic.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final UserMapper userMapper = mock(UserMapper.class);

	private UserFlow userFlow;

	@BeforeEach
	void setUp() {
		userFlow = new UserFlow(userLogic, userAuthLogic, fileAssetStore, userMapper);
	}

	@Test
	void getCurrentUser_shouldMapProfileWithoutLoadingImageWhenImageIsNotSet() {
		User user = user("USER-0001", null);
		UserProfileRdo expected = UserProfileRdo.builder().username("tester").name("테스터").build();
		when(userLogic.getUserProfile("USER-0001")).thenReturn(user);
		when(userMapper.toUserProfileRdo(user, null)).thenReturn(expected);

		UserProfileRdo result = userFlow.getCurrentUser("USER-0001");

		assertThat(result).isSameAs(expected);
		verify(fileAssetStore, never()).findById(anyString());
		verify(userMapper).toUserProfileRdo(user, null);
	}

	@Test
	void getCurrentUser_shouldMapResolvedProfileImage() {
		User user = user("USER-0001", "FILE-0001");
		FileAsset fileAsset = new FileAsset(FileType.PROFILE, "profile.png", "stored.png", "image/png", 10L);
		fileAsset.setId("FILE-0001");
		FileAssetRdo fileAssetRdo = FileAssetRdo.from(fileAsset);
		UserProfileRdo expected = UserProfileRdo.builder().username("tester").profileImage(fileAssetRdo).build();
		when(userLogic.getUserProfile("USER-0001")).thenReturn(user);
		when(fileAssetStore.findById("FILE-0001")).thenReturn(fileAsset);
		when(userMapper.toUserProfileRdo(eq(user), any(FileAssetRdo.class))).thenReturn(expected);

		UserProfileRdo result = userFlow.getCurrentUser("USER-0001");

		assertThat(result).isSameAs(expected);
		verify(fileAssetStore).findById("FILE-0001");
		ArgumentCaptor<FileAssetRdo> captor = ArgumentCaptor.forClass(FileAssetRdo.class);
		verify(userMapper).toUserProfileRdo(eq(user), captor.capture());
		assertThat(captor.getValue().getFileId()).isEqualTo("FILE-0001");
	}

	@Test
	void verifyCurrentUserPassword_shouldForwardAuthenticatedUserIdOnly() {
		UserPasswordVerifyCdo request = new UserPasswordVerifyCdo();
		request.setPassword("Password1!");

		userFlow.verifyCurrentUserPassword("USER-0001", request);

		verify(userLogic).verifyPassword("USER-0001", request);
	}

	@Test
	void updateCurrentUser_shouldUseAuthenticatedUserIdAndMapPasswordFreeProfile() {
		UserProfileUdo request = new UserProfileUdo();
		request.setName("수정된 이름");
		User updatedUser = user("USER-0001", null);
		updatedUser.setPassword("encoded-password");
		UserProfileRdo expected = UserProfileRdo.builder().username("tester").name("수정된 이름").build();
		when(userLogic.updateUserProfile("USER-0001", request)).thenReturn(updatedUser);
		when(userMapper.toUserProfileRdo(updatedUser, null)).thenReturn(expected);

		UserProfileSessionVo result = userFlow.updateCurrentUser("USER-0001", request);

		assertThat(result.userProfileRdo()).isSameAs(expected);
		assertThat(result.tokenSessionVo()).isNull();
		verify(userLogic).updateUserProfile("USER-0001", request);
		verify(userMapper).toUserProfileRdo(updatedUser, null);
		assertThat(UserProfileRdo.class.getDeclaredFields())
			.noneMatch(field -> field.getName().toLowerCase().contains("password"));
		verify(userAuthLogic, never()).logoutAll(anyString());
	}

	@Test
	void updateCurrentUser_shouldLogoutAllSessionsWhenPasswordChanges() {
		UserProfileUdo request = new UserProfileUdo();
		request.setNewPassword("NewPassword1!");
		User updatedUser = user("USER-0001", null);
		TokenSessionVo newSession = new TokenSessionVo("new-session",
			com.seoulchonnom.spec.user.facade.sdo.TokenRdo.builder().refreshToken("new-refresh").build());
		when(userLogic.updateUserProfile("USER-0001", request)).thenReturn(updatedUser);
		when(userMapper.toUserProfileRdo(updatedUser, null)).thenReturn(UserProfileRdo.builder().build());
		when(userAuthLogic.issueAuthenticatedUserRefreshSession("USER-0001")).thenReturn(newSession);

		UserProfileSessionVo result = userFlow.updateCurrentUser("USER-0001", request);

		org.mockito.InOrder inOrder = inOrder(userAuthLogic);
		inOrder.verify(userAuthLogic).logoutAll("USER-0001");
		inOrder.verify(userAuthLogic).issueAuthenticatedUserRefreshSession("USER-0001");
		assertThat(result.tokenSessionVo()).isSameAs(newSession);
	}

	private User user(String id, String profileImageFileId) {
		User user = User.builder()
			.username("tester")
			.name("테스터")
			.password("encoded-password")
			.profileImageFileId(profileImageFileId)
			.build();
		user.setId(id);
		return user;
	}
}
