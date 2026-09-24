package com.seoulchonnom.aggregate.inspection.store.jpo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.inspection.store.jpo.converter.QuestionVersionListConverter;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inspection_question", schema = "slcn", indexes = {
	@Index(name = "idx_inspection_question_enabled_sort", columnList = "enabled,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionJpo extends DomainEntityJpo {
	@Enumerated(EnumType.STRING)
	private QuestionAnswerType answerType;
	private boolean required;
	private int sortOrder;
	private boolean enabled;
	@Convert(converter = QuestionVersionListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<QuestionVersion> versions = new ArrayList<>();
	private int currentVersionNo;
}
