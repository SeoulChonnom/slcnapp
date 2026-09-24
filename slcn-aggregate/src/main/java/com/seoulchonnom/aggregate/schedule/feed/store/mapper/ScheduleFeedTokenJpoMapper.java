package com.seoulchonnom.aggregate.schedule.feed.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.schedule.feed.store.jpo.ScheduleFeedTokenJpo;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface ScheduleFeedTokenJpoMapper {
	ScheduleFeedTokenJpo toJpo(ScheduleFeedToken token);

	ScheduleFeedToken toDomain(ScheduleFeedTokenJpo tokenJpo);

	@AfterMapping
	default void mapInheritedFields(ScheduleFeedTokenJpo tokenJpo, @MappingTarget ScheduleFeedToken token) {
		token.setId(tokenJpo.getId());
		token.setEntityVersion(tokenJpo.getEntityVersion());
		if (tokenJpo.getRegisteredTime() != null) {
			token.setRegisteredTime(tokenJpo.getRegisteredTime());
		}
		if (tokenJpo.getModifiedTime() != null) {
			token.setModifiedTime(tokenJpo.getModifiedTime());
		}
	}
}
