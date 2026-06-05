package com.seoulchonnom.aggregate.user.store.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.user.store.jpo.UserJpo;
import com.seoulchonnom.spec.user.entity.User;

@SpringJUnitConfig
@ContextConfiguration(classes = {UserJpoMapperImpl.class, AuthorityJpoMapperImpl.class})
class UserJpoMapperTest {

	@Autowired
	private UserJpoMapper userJpoMapper;

	@Test
	void toDomain_shouldPreserveInheritedFields() {
		UserJpo userJpo = new UserJpo();
		userJpo.setId("USER-0002");
		userJpo.setEntityVersion(3L);
		userJpo.setRegisteredTime(100L);
		userJpo.setModifiedTime(200L);
		userJpo.setUsername("string");
		userJpo.setName("string");
		userJpo.setPassword("$2a$10$hash");

		User user = userJpoMapper.toDomain(userJpo);

		assertThat(user.getId()).isEqualTo("USER-0002");
		assertThat(user.getEntityVersion()).isEqualTo(3L);
		assertThat(user.getRegisteredTime()).isEqualTo(100L);
		assertThat(user.getModifiedTime()).isEqualTo(200L);
		assertThat(user.getUsername()).isEqualTo("string");
		assertThat(user.getPassword()).isEqualTo("$2a$10$hash");
	}
}
