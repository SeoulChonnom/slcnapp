package com.seoulchonnom.aggregate.user.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginDoc;
import com.seoulchonnom.spec.user.entity.UserLogin;

@Mapper(componentModel = SPRING)
public interface UserLoginDocMapper {
	ZoneId ZONE_ID = ZoneId.systemDefault();

	@Mapping(target = "id", source = "userId")
	UserLoginDoc toDoc(UserLogin userLogin);

	@Mapping(target = "userId", source = "id")
	UserLogin toDomain(UserLoginDoc userLoginDoc);

	default LocalDateTime map(long epochMillis) {
		if (epochMillis <= 0L) {
			return null;
		}

		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE_ID);
	}

	default long map(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return 0L;
		}

		return localDateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
	}
}
