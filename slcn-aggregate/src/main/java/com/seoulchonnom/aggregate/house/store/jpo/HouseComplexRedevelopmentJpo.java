package com.seoulchonnom.aggregate.house.store.jpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파생 테이블. 5단계(정비사업 동기화)가 채울 자리다. 지금은 구현체가 없어 항상 비어 있다.
 * DomainEntityJpo를 상속하지 않고 @Version도 없다.
 */
@Entity
@Table(name = "house_complex_redevelopment", schema = "slcn")
@IdClass(HouseComplexRedevelopmentId.class)
@Getter
@NoArgsConstructor
public class HouseComplexRedevelopmentJpo {
	@Id
	@Column(name = "complex_id", nullable = false)
	private String complexId;

	@Id
	@Column(name = "redevelopment_id", nullable = false)
	private String redevelopmentId;

	@Column(name = "distance_m", nullable = false)
	private int distanceM;
}
