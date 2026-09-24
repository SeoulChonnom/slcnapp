package com.seoulchonnom.spec.inspection.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 한 회차에서 확인한 매물 한 건. ID는 UUID다 — id_sequence는 상한이 0xFFFF라
 * 임장 수 x 매물 수로 늘어나는 값에 맞지 않는다.
 *
 * 문답은 answers VO 목록으로 이 행 안에 산다. 매물을 지우면 문답도 함께 사라진다.
 * complexName에 유니크 제약을 걸지 않는다. 같은 임장에 같은 단지의 매물이 여러 건인 것이 정상이다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ViewedProperty extends DomainEntity {
	private String inspectionVisitId;
	private String complexName;
	private String name;
	private String memo;
	private String oneLineReview;
	private String pros;
	private String cons;
	private Integer interestLevel;
	private InspectionStatus status;
	private int sortOrder;
	@Builder.Default
	private List<PropertyAnswer> answers = new ArrayList<>();
	/**
	 * answers를 순회하면 다시 구할 수 있지만 컬럼으로 저장한다.
	 * 목록 화면의 미완료 집계가 answers JSON을 읽지 않고 정수 합산으로 끝나게 하기 위해서다.
	 */
	private int requiredAnswerCount;
	private int unansweredRequiredCount;

	public ViewedProperty(String inspectionVisitId, String complexName, String name, int sortOrder) {
		super();
		this.inspectionVisitId = inspectionVisitId;
		this.complexName = complexName;
		this.name = name;
		this.sortOrder = sortOrder;
		this.status = InspectionStatus.DRAFT;
		this.answers = new ArrayList<>();
	}

	public void update(String complexName, String name, String memo, String oneLineReview, String pros, String cons,
		Integer interestLevel) {
		this.complexName = complexName;
		this.name = name;
		this.memo = memo;
		this.oneLineReview = oneLineReview;
		this.pros = pros;
		this.cons = cons;
		this.interestLevel = interestLevel;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeStatus(InspectionStatus status) {
		this.status = status;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}

	public Optional<PropertyAnswer> findAnswer(String questionId) {
		if (answers == null || questionId == null) {
			return Optional.empty();
		}
		return answers.stream()
			.filter(answer -> questionId.equals(answer.getQuestionId()))
			.findFirst();
	}

	/**
	 * answers가 바뀌는 모든 경로가 이 메서드를 거친다. 리스트와 파생 카운트가
	 * 서로 어긋난 채 저장되는 일이 없도록 갱신 지점을 하나로 둔다.
	 */
	public void refreshAnswerCounts() {
		if (answers == null) {
			this.requiredAnswerCount = 0;
			this.unansweredRequiredCount = 0;
			return;
		}
		int required = 0;
		int unanswered = 0;
		for (PropertyAnswer answer : answers) {
			if (!answer.isRequired()) {
				continue;
			}
			required++;
			if (!answer.isAnswered()) {
				unanswered++;
			}
		}
		this.requiredAnswerCount = required;
		this.unansweredRequiredCount = unanswered;
		this.modifiedTime = System.currentTimeMillis();
	}
}
