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
 * 복합 기본키 대신 UUID 단일 PK + 유니크 인덱스를 쓴다.
 * 이 저장소의 모든 JPO가 EntityJpo(단일 String @Id)를 상속하므로
 * @IdClass를 도입하면 이 테이블만 다른 모양이 된다.
 */
@Entity
@Table(name = "inspection_visit_tag", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_inspection_visit_tag",
		columnNames = {"inspection_visit_id", "tag_id"}),
	indexes = {
		@Index(name = "idx_inspection_visit_tag_tag", columnList = "tag_id")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionVisitTagJpo extends EntityJpo {
	private String inspectionVisitId;
	private String tagId;
}
