package com.seoulchonnom.aggregate.user.store.doc;

import java.time.LocalDateTime;

public class UserLoginHistoryDoc {
	private String userId;
	private LocalDateTime loginTime;
	private boolean loginSuccess;
}
