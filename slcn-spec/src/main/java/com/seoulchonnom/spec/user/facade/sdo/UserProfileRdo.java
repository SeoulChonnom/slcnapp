package com.seoulchonnom.spec.user.facade.sdo;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileRdo {
	private String username;
	private String name;
	private FileAssetRdo profileImage;
}
