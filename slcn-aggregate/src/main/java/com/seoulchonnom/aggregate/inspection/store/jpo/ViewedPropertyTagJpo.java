package com.seoulchonnom.aggregate.inspection.store.jpo;

import com.seoulchonnom.aggregate.common.entity.EntityJpo;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * inspectionVisitId를 함께 들고 있다. 임장 단위 삭제와 목록 태그 조회가
 * 매물 조인 없이 끝나게 하기 위한 비정규화다.
 */
@Entity
@Table(name = "viewed_property_tag", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_viewed_property_tag",
		columnNames = {"viewed_property_id", "tag_id"}),
	indexes = {
		@Index(name = "idx_viewed_property_tag_tag", columnList = "tag_id"),
		@Index(name = "idx_viewed_property_tag_visit", columnList = "inspection_visit_id")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewedPropertyTagJpo extends EntityJpo {
	private String viewedPropertyId;
	private String inspectionVisitId;
	private String tagId;
}
