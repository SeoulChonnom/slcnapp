package com.seoulchonnom.slcnapp.user.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Token {
	private int userId;
	private String refreshToken;
	private String accessToken;
}
