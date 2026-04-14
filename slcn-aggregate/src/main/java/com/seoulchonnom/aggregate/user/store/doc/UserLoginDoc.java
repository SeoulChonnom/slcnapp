package com.seoulchonnom.aggregate.user.store.doc;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "user_login")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginDoc {
	@Id

	private String id;
	private LocalDateTime lastLoginTime;

	private int loginFailCount;
	private LocalDateTime lastLoginFailTime;
}
