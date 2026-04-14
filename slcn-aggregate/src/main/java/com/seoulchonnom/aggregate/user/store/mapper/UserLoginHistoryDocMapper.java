package com.seoulchonnom.aggregate.user.store.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginHistoryDoc;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;

@Component
public class UserLoginHistoryDocMapper {
	private final ZoneId zoneId = ZoneId.systemDefault();

	public UserLoginHistoryDoc toDoc(UserLoginHistory userLoginHistory) {
		return new UserLoginHistoryDoc(
			null,
			userLoginHistory.getUserId(),
			toLocalDateTime(userLoginHistory.getLoginTime()),
			userLoginHistory.isLoginSuccess());
	}

	private LocalDateTime toLocalDateTime(long epochMillis) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId);
	}
}
