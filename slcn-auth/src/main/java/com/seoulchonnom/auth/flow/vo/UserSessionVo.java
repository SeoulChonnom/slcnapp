package com.seoulchonnom.auth.flow.vo;

import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserSessionVo {
	private final TokenRdo tokenRdo;
	private final UserRdo userRdo;
}
