package com.seoulchonnom.spec.schedule.feed.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCreatedRdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "일정 피드 API", description = "일정 ICS 피드 토큰 관리")
@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface ScheduleFeedFacade {
	@Operation(summary = "일정 피드 생성", description = "외부 캘린더 구독용 일정 피드를 생성합니다.")
	ResponseEntity<ScheduleFeedCreatedRdo> createFeed(ScheduleFeedCdo scheduleFeedCdo);

	@Operation(summary = "일정 피드 목록 조회", description = "생성된 일정 피드의 안전한 메타데이터를 조회합니다.")
	ResponseEntity<List<ScheduleFeedRdo>> getFeeds();

	@Operation(summary = "일정 피드 폐기", description = "외부 캘린더가 더 이상 일정 피드를 조회하지 못하도록 폐기합니다.")
	ResponseEntity<Void> deleteFeed(String feedId);
}
