package com.seoulchonnom.spec.user.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.user.entity.User;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;

class UserMapperTest {
	private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

	@Test
	void toUserProfileRdo_shouldMapUserAndResolvedProfileImage() {
		User user = User.builder()
			.username("tester")
			.name("테스터")
			.profileImageFileId("profile-file-1")
			.authorityList(List.of())
			.build();
		FileAssetRdo profileImage = new FileAssetRdo(
			"profile-file-1", FileType.PROFILE, "profile.png", "stored-profile.png",
			"profile/stored-profile.png", "image/png", 10L);

		UserProfileRdo userProfileRdo = userMapper.toUserProfileRdo(user, profileImage);

		assertThat(userProfileRdo.getUsername()).isEqualTo("tester");
		assertThat(userProfileRdo.getName()).isEqualTo("테스터");
		assertThat(userProfileRdo.getProfileImage()).isSameAs(profileImage);
	}
}
