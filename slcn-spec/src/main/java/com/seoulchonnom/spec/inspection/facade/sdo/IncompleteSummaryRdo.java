package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DRAFT에서 "무엇이 남았는지"를 화면에 적기 위한 요약. 완료 검증과 같은 기준을 쓴다.
 *
 * 매물 레벨과 임장 레벨을 함께 담는다. 매물 필드만 담으면 재방문 의사 미입력 때문에
 * 임장을 완료할 수 없는 상황을 화면이 설명하지 못한다.
 *
 * 목록 응답에는 개수 필드만 채운다. 목록 N행마다 질문 문구를 끌어오면
 * 집계 질의가 답변 본문 조회로 바뀐다.
 */
@Getter
@Setter
@NoArgsConstructor
public class IncompleteSummaryRdo {
	/**
	 * 매물: required = true AND answered = false인 답변 행 수
	 */
	private int unansweredRequiredCount;
	/**
	 * 매물: 위 행들의 질문 목록. 상세 응답에만 채운다
	 */
	private List<UnansweredQuestionRdo> unansweredRequiredQuestions = new ArrayList<>();
	/**
	 * 매물: complexName / name / interestLevel 중 비어 있는 필드명
	 */
	private List<String> missingFields = new ArrayList<>();
	/**
	 * 임장: 이 임장에서 status = DRAFT인 매물 수
	 */
	private int draftPropertyCount;
	/**
	 * 임장: visitedAt / revisitIntent 중 비어 있는 필드명
	 */
	private List<String> visitMissingFields = new ArrayList<>();
	/**
	 * 지역: 이 지역에서 status = DRAFT인 임장 수
	 */
	private int draftVisitCount;
}
