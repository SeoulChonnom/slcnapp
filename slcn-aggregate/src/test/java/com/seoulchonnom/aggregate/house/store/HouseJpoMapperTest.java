package com.seoulchonnom.aggregate.house.store;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.store.jpo.HouseBatchRunJpo;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexJpo;
import com.seoulchonnom.aggregate.house.store.jpo.converter.AddressOverrideConverter;
import com.seoulchonnom.aggregate.house.store.jpo.converter.JsonMapConverter;
import com.seoulchonnom.aggregate.house.store.mapper.HouseBatchRunJpoMapper;
import com.seoulchonnom.aggregate.house.store.mapper.HouseBatchRunJpoMapperImpl;
import com.seoulchonnom.aggregate.house.store.mapper.HouseCollectUnitJpoMapperImpl;
import com.seoulchonnom.aggregate.house.store.mapper.HouseComplexJpoMapper;
import com.seoulchonnom.aggregate.house.store.mapper.HouseComplexJpoMapperImpl;
import com.seoulchonnom.aggregate.house.store.mapper.HouseTradeJpoMapperImpl;
import com.seoulchonnom.spec.house.entity.HouseBatchRun;
import com.seoulchonnom.spec.house.entity.HouseCollectUnit;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.HouseTrade;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.BatchStep;
import com.seoulchonnom.spec.house.entity.vo.CollectUnitStatus;
import com.seoulchonnom.spec.house.entity.vo.DealingType;
import com.seoulchonnom.spec.house.entity.vo.FarSource;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

class HouseJpoMapperTest {
	@Test
	void complexMapper_shouldRoundTripAllFieldsIncludingInheritedOnes() {
		HouseComplexJpoMapper mapper = new HouseComplexJpoMapperImpl();
		HouseComplex complex = HouseComplex.builder().aptSeq("11680-283").lawdCd("11680").umdCd("10300")
			.umdName("개포동").bonbun("0652").bubun("0000").aptName("개포우성3차").roadName("개포로")
			.roadBonbun("307").roadBubun("0").roadAddress("서울특별시 강남구 개포로 307")
			.addressOverride(new AddressOverride("서울특별시 강남구 개포로 307", "0652", null))
			.latitude(37.48).longitude(127.05).geocodeStatus(GeocodeStatus.DONE).geocodeAttempts(1)
			.registerStatus(RegisterStatus.DONE).registerAttempts(2).registerSyncedDate(LocalDate.of(2026, 10, 1))
			.buildYear(1984).approvalDate(LocalDate.of(1984, 5, 1)).households(1000)
			.farPct(new BigDecimal("199.50")).bcrPct(new BigDecimal("20.10")).farSource(FarSource.RECAP).build();
		complex.setId("HOUSE_COMPLEX-0001");
		complex.setEntityVersion(3);
		complex.setRegisteredTime(111);
		complex.setModifiedTime(222);

		HouseComplexJpo jpo = mapper.toJpo(complex);
		HouseComplex back = mapper.toDomain(jpo);

		assertThat(jpo.getId()).isEqualTo("HOUSE_COMPLEX-0001");
		assertThat(jpo.getEntityVersion()).isEqualTo(3);
		assertThat(back).usingRecursiveComparison().isEqualTo(complex);
	}

	@Test
	void tradeAndUnitMappers_shouldRoundTrip() {
		HouseTrade trade = HouseTrade.builder().dedupKey("a".repeat(64)).complexId("HOUSE_COMPLEX-0001")
			.lawdCd("11680").dealYm("202609").dealDate(LocalDate.of(2026, 9, 22))
			.exclusiveArea(new BigDecimal("59.97")).areaBucket(AreaBucket.B59).amountManwon(295000L).floor(7)
			.aptDong("101").buildYear(1984).dealingType(DealingType.BROKER).cancelled(true)
			.cancelledDate(LocalDate.of(2026, 9, 30)).outlier(true).removed(true).build();
		HouseTradeJpoMapperImpl tradeMapper = new HouseTradeJpoMapperImpl();
		assertThat(tradeMapper.toDomain(tradeMapper.toJpo(trade))).usingRecursiveComparison().isEqualTo(trade);

		HouseCollectUnit unit = HouseCollectUnit.builder().lawdCd("11680").dealYm("202609")
			.status(CollectUnitStatus.FAILED).lastSuccessDate(LocalDate.of(2026, 10, 1)).attempts(2).build();
		HouseCollectUnitJpoMapperImpl unitMapper = new HouseCollectUnitJpoMapperImpl();
		assertThat(unitMapper.toDomain(unitMapper.toJpo(unit))).usingRecursiveComparison().isEqualTo(unit);
	}

	@Test
	void batchRunMapper_shouldRoundTripDetailMap() {
		HouseBatchRunJpoMapper mapper = new HouseBatchRunJpoMapperImpl();
		HouseBatchRun run = HouseBatchRun.builder().runDate(LocalDate.of(2026, 10, 20)).step(BatchStep.TRADE_COLLECT)
			.status(BatchRunStatus.PARTIAL).apiCalls(12).detail(new LinkedHashMap<>(Map.of("inserted", 3)))
			.startedTime(1).finishedTime(2).error("BUDGET").build();

		HouseBatchRunJpo jpo = mapper.toJpo(run);

		assertThat(mapper.toDomain(jpo)).usingRecursiveComparison().isEqualTo(run);
	}

	@Test
	void converters_shouldRoundTripJsonAndKeepNulls() {
		AddressOverrideConverter overrideConverter = new AddressOverrideConverter();
		AddressOverride override = new AddressOverride("서울특별시 강남구 개포로 307", null, "0001");

		assertThat(overrideConverter.convertToEntityAttribute(overrideConverter.convertToDatabaseColumn(override)))
			.isEqualTo(override);
		assertThat(overrideConverter.convertToDatabaseColumn(null)).isNull();
		assertThat(overrideConverter.convertToEntityAttribute(" ")).isNull();

		JsonMapConverter mapConverter = new JsonMapConverter();
		Map<String, Object> detail = Map.of("inserted", 3, "reason", "SERVICE_KEY_MISSING");
		assertThat(mapConverter.convertToEntityAttribute(mapConverter.convertToDatabaseColumn(detail)))
			.isEqualTo(detail);
		assertThat(mapConverter.convertToDatabaseColumn(null)).isNull();
	}
}
