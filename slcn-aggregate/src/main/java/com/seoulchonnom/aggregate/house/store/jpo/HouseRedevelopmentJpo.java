package com.seoulchonnom.aggregate.house.store.jpo;

import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_redevelopment", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_redevelopment_source_key", columnNames = "source_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseRedevelopmentJpo extends DomainEntityJpo {
	@Column(length = 100, nullable = false)
	private String sourceKey;

	@Column(length = 200, nullable = false)
	private String name;

	/** 자치구 이름에서 정한 시군구 코드. 기존 행이 있어도 컬럼이 추가되도록 NOT NULL을 걸지 않는다. */
	@Column(length = 5)
	private String lawdCd;

	@Column(length = 300)
	private String jibunAddress;

	@Column(length = 300)
	private String roadAddress;

	/** 관리자가 직접 적은 좌표 검색 주소(선택) */
	@Column(length = 300)
	private String geocodeQuery;

	@Column(length = 50)
	private String projectType;

	@Column(length = 50)
	private String stage;

	private Double latitude;

	private Double longitude;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private GeocodeStatus geocodeStatus;

	/** 기존 행이 있어도 컬럼이 추가되도록 래퍼 타입으로 둔다. */
	private Integer geocodeAttempts;

	@Column(length = 500)
	private String sourceUrl;

	private LocalDate syncedDate;
}
