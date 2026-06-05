package com.seoulchonnom.spec.user.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TokenRdo {
	private String userId;
	private String refreshToken;
	private String accessToken;
}
