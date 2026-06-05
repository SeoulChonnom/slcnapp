package com.seoulchonnom.aggregate.user.store.doc;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "user_login_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginHistoryDoc {
	@Id
	private String id;
	private String userId;
	private LocalDateTime loginTime;
	private boolean loginSuccess;
}
