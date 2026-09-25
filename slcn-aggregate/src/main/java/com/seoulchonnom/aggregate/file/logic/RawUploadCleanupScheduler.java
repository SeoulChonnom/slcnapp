package com.seoulchonnom.aggregate.file.logic;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시작만 하고 끝내지 않은 RAW 업로드를 하루 한 번 정리한다.
 * 버킷 라이프사이클(미완료 multipart 7일 중단)이 최종 안전망이고, 이 작업은 자산 메타데이터까지 함께 지운다.
 * 완료됐지만 어떤 여행에도 연결되지 않은 RAW는 지우지 않는다. 기존 이미지 고아 정책과 같게, 규모상 수동 확인이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawUploadCleanupScheduler {
	/**
	 * 세션 URL 만료(30분)보다 충분히 길게 잡아, 느린 업로드를 진행 중에 지우지 않는다.
	 */
	static final Duration STALE_AFTER = Duration.ofHours(24);

	private final RawUploadLogic rawUploadLogic;

	/**
	 * 설정값이 없으면 "-"로 꺼진다. 운영 값은 application.yml에 둔다.
	 */
	@Scheduled(cron = "${slcn.storage.raw.cleanup-cron:-}", zone = "Asia/Seoul")
	public void cleanupStaleUploads() {
		long cutoff = System.currentTimeMillis() - STALE_AFTER.toMillis();
		int cleaned = rawUploadLogic.cleanupPendingRegisteredBefore(cutoff);
		if (cleaned > 0) {
			log.info("Cleaned stale RAW uploads. count={}", cleaned);
		}
	}
}
