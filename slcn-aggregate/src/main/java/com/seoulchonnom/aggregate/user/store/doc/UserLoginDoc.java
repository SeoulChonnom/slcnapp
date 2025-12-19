package com.seoulchonnom.aggregate.user.store.doc;

import java.time.LocalDateTime;

public class UserLoginDoc {
	private String userId;
	private LocalDateTime lastLoginTime;

	private int loginFailCount;
	private LocalDateTime lastLoginFailTime;
}
