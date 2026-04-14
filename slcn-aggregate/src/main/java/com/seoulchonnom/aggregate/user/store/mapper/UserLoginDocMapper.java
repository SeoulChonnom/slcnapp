package com.seoulchonnom.aggregate.user.store.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginDoc;
import com.seoulchonnom.spec.user.entity.UserLogin;

@Component
public class UserLoginDocMapper {
	private final ZoneId zoneId = ZoneId.systemDefault();

	public UserLoginDoc toDoc(UserLogin userLogin) {
		return new UserLoginDoc(
			userLogin.getUserId(),
			toLocalDateTime(userLogin.getLastLoginTime()),
			userLogin.getLoginFailCount(),
			toLocalDateTime(userLogin.getLastLoginFailTime()));
	}

	public UserLogin toDomain(UserLoginDoc userLoginDoc) {
		return new UserLogin(
			userLoginDoc.getUserId(),
			toEpochMillis(userLoginDoc.getLastLoginTime()),
			userLoginDoc.getLoginFailCount(),
			toEpochMillis(userLoginDoc.getLastLoginFailTime()));
	}

	private LocalDateTime toLocalDateTime(long epochMillis) {
		if (epochMillis <= 0L) {
			return null;
		}

		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId);
	}

	private long toEpochMillis(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return 0L;
		}

		return localDateTime.atZone(zoneId).toInstant().toEpochMilli();
	}
}
