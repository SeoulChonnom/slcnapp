package com.seoulchonnom.auth.flow.vo;

import com.seoulchonnom.spec.user.facade.sdo.TokenRdo;

public record TokenSessionVo(String sessionId, TokenRdo tokenRdo) {
}
