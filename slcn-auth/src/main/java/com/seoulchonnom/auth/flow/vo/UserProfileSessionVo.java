package com.seoulchonnom.auth.flow.vo;

import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;

public record UserProfileSessionVo(UserProfileRdo userProfileRdo, TokenSessionVo tokenSessionVo) {
}
