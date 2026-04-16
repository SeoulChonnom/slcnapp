package com.seoulchonnom.aggregate.user.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.mapstruct.Mapper;

import com.seoulchonnom.aggregate.user.store.doc.UserLoginHistoryDoc;
import com.seoulchonnom.spec.user.entity.UserLoginHistory;

@Mapper(componentModel = SPRING)
public interface UserLoginHistoryDocMapper {
	ZoneId ZONE_ID = ZoneId.systemDefault();

	UserLoginHistoryDoc toDoc(UserLoginHistory userLoginHistory);

	default LocalDateTime map(long epochMillis) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE_ID);
	}
}
