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
public class ScheduleFeedCreatedRdo {
	private String id;
	private String name;
	private String feedUrl;
	private long registeredTime;

	public static ScheduleFeedCreatedRdo from(ScheduleFeedToken feedToken, String feedUrl) {
		return new ScheduleFeedCreatedRdo(
			feedToken.getId(),
			feedToken.getName(),
			feedUrl,
			feedToken.getRegisteredTime());
	}
}
