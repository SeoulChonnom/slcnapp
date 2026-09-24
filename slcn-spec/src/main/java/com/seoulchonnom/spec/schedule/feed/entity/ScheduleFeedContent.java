package com.seoulchonnom.spec.schedule.feed.entity;

import java.util.List;

public record ScheduleFeedContent(String feedName, List<ScheduleFeedEvent> events) {
}
