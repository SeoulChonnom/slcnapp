package com.seoulchonnom.spec.user.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserProfileUdo {
	private String name;
	private String newPassword;
	private String profileImageFileId;
}
