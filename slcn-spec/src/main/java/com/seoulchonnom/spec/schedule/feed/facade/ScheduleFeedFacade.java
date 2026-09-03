package com.seoulchonnom.spec.schedule.feed.facade;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCreatedRdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "일정 피드 API", description = "일정 ICS 피드 토큰 관리")
public interface ScheduleFeedFacade {
	@Operation(summary = "일정 피드 생성", description = "외부 캘린더 구독용 일정 피드를 생성합니다.")
	@SecurityRequirement(name = "X-AUTH-TOKEN")
	ResponseEntity<ScheduleFeedCreatedRdo> createFeed(ScheduleFeedCdo scheduleFeedCdo);

	@Operation(summary = "일정 피드 목록 조회", description = "생성된 일정 피드의 안전한 메타데이터를 조회합니다.")
	@SecurityRequirement(name = "X-AUTH-TOKEN")
	ResponseEntity<List<ScheduleFeedRdo>> getFeeds();

	@Operation(summary = "일정 피드 폐기", description = "외부 캘린더가 더 이상 일정 피드를 조회하지 못하도록 폐기합니다.")
	@SecurityRequirement(name = "X-AUTH-TOKEN")
	ResponseEntity<Void> deleteFeed(String feedId);

	@Operation(summary = "일정 ICS 피드 조회", description = "토큰 URL로 외부 캘린더가 구독할 읽기 전용 ICS 피드를 조회합니다.")
	@ApiResponse(responseCode = "200", description = "ICS 피드 조회 성공")
	@ApiResponse(responseCode = "304", description = "ETag 일치, 본문 없음")
	@SecurityRequirements
	ResponseEntity<String> getCalendar(
		@PathVariable("feedToken") String feedToken,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch);
}
