package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.QuestionChoiceSdo;

class InspectionQuestionMapperTest {
	private final InspectionQuestionMapper inspectionQuestionMapper = new InspectionQuestionMapper();

	private static InspectionQuestionCdo cdo() {
		InspectionQuestionCdo cdo = new InspectionQuestionCdo();
		cdo.setAnswerType(QuestionAnswerType.SINGLE_SELECT);
		cdo.setContent("방향은?");
		cdo.setDescription("오후 기준");
		cdo.setChoices(List.of(new QuestionChoiceSdo("SOUTH", "남향", 1)));
		cdo.setUnit("방위");
		cdo.setRequired(true);
		cdo.setSortOrder(2);
		return cdo;
	}

	@Test
	void toInspectionQuestion_shouldStartEnabledWithVersionOne() {
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion("INSPECTION_QUESTION-0001", cdo());

		assertThat(question.getId()).isEqualTo("INSPECTION_QUESTION-0001");
		assertThat(question.isEnabled()).isTrue();
		assertThat(question.getAnswerType()).isEqualTo(QuestionAnswerType.SINGLE_SELECT);
		assertThat(question.getVersions()).hasSize(1);
		assertThat(question.getCurrentVersionNo()).isEqualTo(1);
		assertThat(question.currentVersion().orElseThrow().getChoices()).hasSize(1);
	}

	@Test
	void addVersion_shouldAppendWithoutTouchingAnswerType() {
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion("INSPECTION_QUESTION-0001", cdo());
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		udo.setContent("채광은 어떤가?");

		QuestionVersion version = inspectionQuestionMapper.addVersion(question, udo);

		assertThat(version.getVersionNo()).isEqualTo(2);
		assertThat(version.getContent()).isEqualTo("채광은 어떤가?");
		assertThat(version.getChoices()).isEmpty();
		assertThat(question.getAnswerType()).isEqualTo(QuestionAnswerType.SINGLE_SELECT);
		assertThat(question.findVersion(1).orElseThrow().getContent()).isEqualTo("방향은?");
	}

	@Test
	void toInspectionQuestionRdo_shouldUseCurrentVersionContent() {
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion("INSPECTION_QUESTION-0001", cdo());
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		udo.setContent("채광은 어떤가?");
		inspectionQuestionMapper.addVersion(question, udo);

		InspectionQuestionRdo rdo = inspectionQuestionMapper.toInspectionQuestionRdo(question, null);

		assertThat(rdo.getContent()).isEqualTo("채광은 어떤가?");
		assertThat(rdo.getCurrentVersionNo()).isEqualTo(2);
		assertThat(rdo.getAnswerCount()).isNull();
	}

	/**
	 * C-1: 응답에 실린 entityVersion을 프런트가 그대로 되돌려 보내야 충돌 감지가 가능하다.
	 */
	@Test
	void toInspectionQuestionRdo_shouldExposeEntityVersionForConflictDetection() {
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion("INSPECTION_QUESTION-0001", cdo());
		question.setEntityVersion(3L);

		InspectionQuestionRdo rdo = inspectionQuestionMapper.toInspectionQuestionRdo(question, null);

		assertThat(rdo.getEntityVersion()).isEqualTo(3L);
	}

	@Test
	void toInspectionQuestionRdo_shouldTolerateQuestionWithoutVersion() {
		InspectionQuestion question = new InspectionQuestion("INSPECTION_QUESTION-0002", QuestionAnswerType.TEXT,
			false, 1);

		InspectionQuestionRdo rdo = inspectionQuestionMapper.toInspectionQuestionRdo(question, null);

		assertThat(rdo.getContent()).isNull();
		assertThat(rdo.getChoices()).isEmpty();
	}

	@Test
	void toInspectionQuestionVersionRdo_shouldMarkOnlyTheCurrentOne() {
		InspectionQuestion question = inspectionQuestionMapper.toInspectionQuestion("INSPECTION_QUESTION-0001", cdo());
		InspectionQuestionContentUdo udo = new InspectionQuestionContentUdo();
		udo.setContent("채광은 어떤가?");
		inspectionQuestionMapper.addVersion(question, udo);

		InspectionQuestionVersionRdo v1 = inspectionQuestionMapper.toInspectionQuestionVersionRdo(question,
			question.findVersion(1).orElseThrow(), 7);
		InspectionQuestionVersionRdo v2 = inspectionQuestionMapper.toInspectionQuestionVersionRdo(question,
			question.findVersion(2).orElseThrow(), 0);

		assertThat(v1.isCurrent()).isFalse();
		assertThat(v1.getAnswerCount()).isEqualTo(7);
		assertThat(v1.getUnit()).isEqualTo("방위");
		assertThat(v2.isCurrent()).isTrue();
	}
}
