package com.seoulchonnom.aggregate.house.store.jpo;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.house.store.jpo.converter.AddressOverrideConverter;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.FarSource;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_complex", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_complex_apt_seq", columnNames = "apt_seq"),
	indexes = {
		@Index(name = "idx_house_complex_geo", columnList = "latitude, longitude"),
		@Index(name = "idx_house_complex_district", columnList = "lawd_cd, umd_cd"),
		@Index(name = "idx_house_complex_name", columnList = "apt_name")
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseComplexJpo extends DomainEntityJpo {
	/** 대체 키(ALT:...)는 20자를 넘어 50자로 잡는다. */
	@Column(length = 50, nullable = false)
	private String aptSeq;

	@Column(length = 5, nullable = false)
	private String lawdCd;

	@Column(length = 5)
	private String umdCd;

	@Column(length = 50)
	private String umdName;

	@Column(length = 4)
	private String bonbun;

	@Column(length = 4)
	private String bubun;

	@Column(length = 100)
	private String aptName;

	@Column(length = 100)
	private String roadName;

	@Column(length = 10)
	private String roadBonbun;

	@Column(length = 10)
	private String roadBubun;

	@Column(length = 200)
	private String roadAddress;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = AddressOverrideConverter.class)
	private AddressOverride addressOverride;

	private Double latitude;

	private Double longitude;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private GeocodeStatus geocodeStatus;

	@Column(nullable = false)
	private int geocodeAttempts;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, nullable = false)
	private RegisterStatus registerStatus;

	@Column(nullable = false)
	private int registerAttempts;

	private LocalDate registerSyncedDate;

	private Integer buildYear;

	private LocalDate approvalDate;

	private Integer households;

	@Column(precision = 7, scale = 2)
	private BigDecimal farPct;

	@Column(precision = 7, scale = 2)
	private BigDecimal bcrPct;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private FarSource farSource;
}
