package com.seoulchonnom.aggregate.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionCategoryBackfillLogic;

import lombok.extern.slf4j.Slf4j;

/**
 * 기존 매물 문답 대분류 백필(계획 §5)을 배포 시 플래그로 켠다.
 * ObjectStorageConfiguration.objectStorageMigrationRunner와 같은 방식이다 - 반복 실행이
 * 필요 없고 진행 결과를 배포 로그에서 바로 봐야 하므로 기동 시 1회로 둔다. 한 번 돌리고
 * 나면 플래그를 내린다. unresolved/failed가 남으면 관리자가 분류 지정을 보완하거나
 * 재기동해 한 번 더 돌린다 - 재실행은 안전하다.
 */
@Slf4j
@Configuration
public class InspectionBackfillConfiguration {
	@Bean
	@ConditionalOnProperty(name = "slcn.inspection.category-backfill.enabled", havingValue = "true")
	public ApplicationRunner inspectionQuestionCategoryBackfillRunner(
		InspectionQuestionCategoryBackfillLogic inspectionQuestionCategoryBackfillLogic) {
		return args -> log.info("Inspection question category backfill finished. {}",
			inspectionQuestionCategoryBackfillLogic.backfill());
	}
}
