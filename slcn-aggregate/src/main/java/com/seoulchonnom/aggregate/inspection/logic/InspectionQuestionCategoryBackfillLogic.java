package com.seoulchonnom.aggregate.inspection.logic;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionCategoryStore;
import com.seoulchonnom.aggregate.inspection.store.InspectionQuestionStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

import lombok.extern.slf4j.Slf4j;

/**
 * 기존 매물 문답 스냅샷에 분류를 채우는 1회성 백필(계획 §5, 결정 2 B안). 관리자가 질문
 * 전체에 분류를 지정한 뒤 배포 시 플래그로 켠다. 여러 번 실행해도 안전하다 -
 * {@link PropertyAnswer#assignCategory}가 이미 채워진 답변은 건드리지 않는다.
 *
 * 매물마다 별도 트랜잭션으로 처리한다. {@code @Transactional}은 같은 빈 안에서 자기
 * 자신을 호출하면 프록시를 거치지 않아 적용되지 않으므로(self-invocation), 이 클래스는
 * {@link TransactionTemplate}을 {@code PROPAGATION_REQUIRES_NEW}로 직접 써서 매물 한 건의
 * 충돌·예외가 다른 매물의 커밋을 막지 않게 한다.
 */
@Slf4j
@Service
public class InspectionQuestionCategoryBackfillLogic {
	private final InspectionQuestionStore inspectionQuestionStore;
	private final InspectionQuestionCategoryStore inspectionQuestionCategoryStore;
	private final ViewedPropertyStore viewedPropertyStore;
	private final TransactionTemplate requiresNewTransactionTemplate;

	public InspectionQuestionCategoryBackfillLogic(InspectionQuestionStore inspectionQuestionStore,
		InspectionQuestionCategoryStore inspectionQuestionCategoryStore, ViewedPropertyStore viewedPropertyStore,
		PlatformTransactionManager transactionManager) {
		this.inspectionQuestionStore = inspectionQuestionStore;
		this.inspectionQuestionCategoryStore = inspectionQuestionCategoryStore;
		this.viewedPropertyStore = viewedPropertyStore;
		this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
		this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * 매물마다 별도 트랜잭션으로 처리한 결과를 집계한다. 분류 우선순위는
	 * failed &gt; unresolved &gt; updated &gt; skipped다(계획 §5-6) - 예외가 난 매물은 무엇이
	 * 바뀌었는지 알 수 없어 failed만 세고, 나머지는 처리 결과 중 가장 심각한 상태 하나로만
	 * 배타적으로 센다.
	 */
	public BackfillReport backfill() {
		List<InspectionQuestion> questions = inspectionQuestionStore.findAll();
		List<String> unassignedQuestionIds = questions.stream()
			.filter(question -> question.getCategoryId() == null)
			.map(InspectionQuestion::getId)
			.toList();
		if (!unassignedQuestionIds.isEmpty()) {
			// 사전 점검 실패(계획 §5-1). 마스터 질문 자체가 미분류라 백필이 무엇을 채워야 할지
			// 알 수 없다. 관리자가 분류 지정을 마칠 때까지 아무것도 건드리지 않고 중단한다.
			// 리포트는 실행하지 않은 것과 같은 모양(전부 0)이라 이 WARN 로그가 "중단"임을
			// 구분하는 유일한 신호다 - 운영 절차(계획 §5)는 이 로그를 보고 재기동 시점을 정한다.
			log.warn("Inspection question category backfill aborted: master questions without categoryId exist. "
				+ "questionIds={}", unassignedQuestionIds);
			return new BackfillReport(0, 0, 0, 0);
		}

		Map<String, InspectionQuestion> questionsById = questions.stream()
			.collect(Collectors.toMap(InspectionQuestion::getId, Function.identity()));
		Map<String, InspectionQuestionCategory> categoriesById = inspectionQuestionCategoryStore.findAll().stream()
			.collect(Collectors.toMap(InspectionQuestionCategory::getId, Function.identity()));

		int updated = 0;
		int skipped = 0;
		int unresolved = 0;
		int failed = 0;

		for (String propertyId : viewedPropertyStore.findAllIds()) {
			try {
				PropertyOutcome outcome = requiresNewTransactionTemplate
					.execute(status -> processProperty(propertyId, questionsById, categoriesById));
				if (outcome.unresolved()) {
					unresolved++;
				} else if (outcome.changed()) {
					updated++;
				} else {
					skipped++;
				}
			} catch (Exception e) {
				// 한 매물의 충돌/예외가 나머지 매물 처리를 막지 않는다. 다시 실행하면 이어서
				// 처리된다(ViewedPropertyStore.save가 낙관적 잠금 충돌을 ViewedPropertyConflictException으로
				// 바꾼다).
				log.error("Inspection question category backfill failed for property. propertyId={}, exception={}",
					propertyId, e.getClass().getSimpleName(), e);
				failed++;
			}
		}

		return new BackfillReport(updated, skipped, unresolved, failed);
	}

	/**
	 * 매물 한 건 처리. 별도 트랜잭션(REQUIRES_NEW) 안에서 돈다.
	 *
	 * 답변 중 일부만 마스터에서 분류를 찾지 못해도, 해결 가능한 나머지 답변은 채워서
	 * 저장한다 - 매물은 unresolved로 세되, 다음 재실행에서 이미 채운 답변까지 다시 손대지
	 * 않는다(계획 §5-4 "값이 있으면 건드리지 않는다").
	 */
	private PropertyOutcome processProperty(String propertyId, Map<String, InspectionQuestion> questionsById,
		Map<String, InspectionQuestionCategory> categoriesById) {
		ViewedProperty property = viewedPropertyStore.findById(propertyId);
		boolean changed = false;
		boolean unresolved = false;

		for (PropertyAnswer answer : property.getAnswers()) {
			if (answer.getCategoryId() != null) {
				continue;
			}
			InspectionQuestion question = questionsById.get(answer.getQuestionId());
			InspectionQuestionCategory category = question == null ? null
				: categoriesById.get(question.getCategoryId());
			if (category == null) {
				// 질문이 마스터에 없거나(물리 삭제 경로는 없지만 방어적으로 대비), 질문이
				// 가리키는 분류를 찾을 수 없는 경우다. 이 답변은 건너뛰고 매물 전체를
				// unresolved로 센다.
				unresolved = true;
				continue;
			}
			if (answer.assignCategory(category.getId(), category.getName(), category.getSortOrder())) {
				changed = true;
			}
		}

		if (changed) {
			// 바뀐 답변이 없는 매물은 저장하지 않는다(계획 §5-5) - @Version을 헛되이 올리지 않는다.
			viewedPropertyStore.save(property);
		}
		return new PropertyOutcome(changed, unresolved);
	}

	private record PropertyOutcome(boolean changed, boolean unresolved) {
	}

	/**
	 * 백필 결과. 배포 로그 한 줄로 확인할 수 있어야 하므로 개수만 센다
	 * (FileAssetMigrationLogic.MigrationReport와 같은 방식).
	 *
	 * 사전 점검에서 중단됐을 때도 이 레코드는 전부 0이다 - 실행할 매물이 없었던 정상 케이스와
	 * 모양이 같으므로, "중단"인지는 이 값이 아니라 backfill() 호출 시 남는 WARN 로그로
	 * 구분해야 한다.
	 */
	public record BackfillReport(int updated, int skipped, int unresolved, int failed) {
	}
}
