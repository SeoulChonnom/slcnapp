package com.seoulchonnom.slcnapp.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

@RedisHash("token")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken {
	@Id
	private Integer id;

	@Indexed
	private String token;

	@TimeToLive
	private Long expiration;

	public void updateToken(String newToken) {
		token = newToken;
	}
}
