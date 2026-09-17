package com.seoulchonnom.spec.inspection.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문의 축. 문구 이력은 versions VO 목록으로 같은 행 안에 산다.
 * answerType은 생성 시 확정되고 이후 바꿀 수 없다.
 * 물리 삭제 경로를 두지 않는다 — 잘못 만든 질문은 enabled=false로 내린다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InspectionQuestion extends DomainEntity {
	private QuestionAnswerType answerType;
	private boolean required;
	private int sortOrder;
	private boolean enabled;
	@Builder.Default
	private List<QuestionVersion> versions = new ArrayList<>();
	private int currentVersionNo;

	public InspectionQuestion(String id, QuestionAnswerType answerType, boolean required, int sortOrder) {
		super(id);
		this.answerType = answerType;
		this.required = required;
		this.sortOrder = sortOrder;
		this.enabled = true;
		this.versions = new ArrayList<>();
	}

	/**
	 * 기존 버전은 건드리지 않고 뒤에 덧붙인다. 이것이 요구사항 §20을 지키는 최소 규칙이며,
	 * JSON 컬럼이라 DB가 대신 막아주지 않으므로 여기서 강제한다.
	 *
	 * @return 추가된 버전. versionNo는 현재 목록 크기 + 1로 채번된다
	 */
	public QuestionVersion addVersion(String content, String description, List<QuestionChoice> choices, String unit) {
		if (versions == null) {
			versions = new ArrayList<>();
		}
		QuestionVersion version = new QuestionVersion(versions.size() + 1, content, description,
			choices == null ? new ArrayList<>() : new ArrayList<>(choices), unit);
		versions.add(version);
		this.currentVersionNo = version.getVersionNo();
		this.modifiedTime = System.currentTimeMillis();
		return version;
	}

	public Optional<QuestionVersion> currentVersion() {
		return findVersion(currentVersionNo);
	}

	public Optional<QuestionVersion> findVersion(int versionNo) {
		if (versions == null) {
			return Optional.empty();
		}
		return versions.stream()
			.filter(version -> version.getVersionNo() == versionNo)
			.findFirst();
	}

	public void changePolicy(boolean required, int sortOrder) {
		this.required = required;
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeEnabled(boolean enabled) {
		this.enabled = enabled;
		this.modifiedTime = System.currentTimeMillis();
	}
}
