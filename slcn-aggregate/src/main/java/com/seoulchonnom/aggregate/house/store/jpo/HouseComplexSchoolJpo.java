package com.seoulchonnom.aggregate.house.store.jpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파생 테이블. 4단계가 DELETE + INSERT(HouseComplexSchoolRepository)로 통째로 다시 만든다.
 * DomainEntityJpo를 상속하지 않고 @Version도 없다. JPA로는 읽기만 하고 save하지 않는다.
 */
@Entity
@Table(name = "house_complex_school", schema = "slcn")
@IdClass(HouseComplexSchoolId.class)
@Getter
@NoArgsConstructor
public class HouseComplexSchoolJpo {
	@Id
	@Column(name = "complex_id", nullable = false)
	private String complexId;

	/** E, M, H */
	@Id
	@Column(name = "level", length = 5, nullable = false)
	private String level;

	@Column(name = "school_id", nullable = false)
	private String schoolId;

	@Column(name = "distance_m", nullable = false)
	private int distanceM;
}
