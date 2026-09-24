package com.seoulchonnom.aggregate.schedule.feed.store.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.schedule.feed.store.jpo.ScheduleFeedTokenJpo;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

@SpringJUnitConfig
@ContextConfiguration(classes = ScheduleFeedTokenJpoMapperImpl.class)
class ScheduleFeedTokenJpoMapperTest {
	@Autowired
	private ScheduleFeedTokenJpoMapper scheduleFeedTokenJpoMapper;

	@Test
	void toDomain_shouldPreserveManagedFields() {
		ScheduleFeedTokenJpo scheduleFeedTokenJpo = new ScheduleFeedTokenJpo();
		scheduleFeedTokenJpo.setId("FEED-0001");
		scheduleFeedTokenJpo.setEntityVersion(5L);
		scheduleFeedTokenJpo.setRegisteredTime(100L);
		scheduleFeedTokenJpo.setModifiedTime(200L);
		scheduleFeedTokenJpo.setName("Google Calendar");
		scheduleFeedTokenJpo.setTokenHash("sha256-hex");

		ScheduleFeedToken token = scheduleFeedTokenJpoMapper.toDomain(scheduleFeedTokenJpo);

		assertThat(token.getId()).isEqualTo("FEED-0001");
		assertThat(token.getEntityVersion()).isEqualTo(5L);
		assertThat(token.getRegisteredTime()).isEqualTo(100L);
		assertThat(token.getModifiedTime()).isEqualTo(200L);
		assertThat(token.getName()).isEqualTo("Google Calendar");
		assertThat(token.getTokenHash()).isEqualTo("sha256-hex");
	}

	@Test
	void toJpo_shouldPreserveManagedFields() {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name("Apple Calendar")
			.tokenHash("another-sha256-hex")
			.build();
		token.setId("FEED-0002");
		token.setEntityVersion(7L);
		token.setRegisteredTime(300L);
		token.setModifiedTime(400L);

		ScheduleFeedTokenJpo scheduleFeedTokenJpo = scheduleFeedTokenJpoMapper.toJpo(token);

		assertThat(scheduleFeedTokenJpo.getId()).isEqualTo("FEED-0002");
		assertThat(scheduleFeedTokenJpo.getEntityVersion()).isEqualTo(7L);
		assertThat(scheduleFeedTokenJpo.getRegisteredTime()).isEqualTo(300L);
		assertThat(scheduleFeedTokenJpo.getModifiedTime()).isEqualTo(400L);
		assertThat(scheduleFeedTokenJpo.getName()).isEqualTo("Apple Calendar");
		assertThat(scheduleFeedTokenJpo.getTokenHash()).isEqualTo("another-sha256-hex");
	}
}
