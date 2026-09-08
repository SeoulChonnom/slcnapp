package com.seoulchonnom.spec.schedule.feed.facade.sdo;

import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleFeedRdo {
	private String id;
	private String name;
	private long registeredTime;

	public static ScheduleFeedRdo from(ScheduleFeedToken feedToken) {
		return new ScheduleFeedRdo(
			feedToken.getId(),
			feedToken.getName(),
			feedToken.getRegisteredTime());
	}
}
