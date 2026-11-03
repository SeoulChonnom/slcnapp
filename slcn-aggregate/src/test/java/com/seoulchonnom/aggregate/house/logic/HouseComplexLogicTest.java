package com.seoulchonnom.aggregate.house.logic;

import static com.seoulchonnom.aggregate.house.collect.TradeRecords.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

class HouseComplexLogicTest {
	private final HouseComplexStore store = mock(HouseComplexStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final HouseComplexLogic logic = new HouseComplexLogic(store, idGenerator);
	private final TradeRecord latest = record("11680-283", LocalDate.of(2026, 9, 22), 295000);

	private HouseComplex existing() {
		HouseComplex complex = HouseComplex.builder().aptSeq("11680-283").lawdCd("11680").umdCd("10300")
			.umdName("개포동").bonbun("0652").bubun("0000").aptName("개포우성3차").roadName("개포로")
			.roadBonbun("307").roadBubun("0").buildYear(1984).geocodeStatus(GeocodeStatus.DONE)
			.registerStatus(RegisterStatus.DONE).build();
		complex.setId("HOUSE_COMPLEX-0001");
		return complex;
	}

	@Test
	void resolve_shouldCreateNewComplexWithSequenceIdAndPendingStatuses() {
		when(store.findByAptSeqIn(any())).thenReturn(Map.of());
		when(idGenerator.nextDomainId("HOUSE_COMPLEX")).thenReturn("HOUSE_COMPLEX-0001");
		when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ComplexResolution result = logic.resolve("11680", Map.of("11680-283", latest));

		ArgumentCaptor<HouseComplex> saved = ArgumentCaptor.forClass(HouseComplex.class);
		verify(store).save(saved.capture());
		HouseComplex complex = saved.getValue();
		assertThat(complex.getId()).isEqualTo("HOUSE_COMPLEX-0001");
		assertThat(complex.getAptSeq()).isEqualTo("11680-283");
		assertThat(complex.getLawdCd()).isEqualTo("11680");
		assertThat(complex.getAptName()).isEqualTo("개포우성3차");
		assertThat(complex.getUmdCd()).isEqualTo("10300");
		assertThat(complex.getBonbun()).isEqualTo("0652");
		assertThat(complex.getRoadName()).isEqualTo("개포로");
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.PENDING);
		assertThat(complex.getGeocodeAttempts()).isZero();
		assertThat(result.complexIdByAptSeq()).containsEntry("11680-283", "HOUSE_COMPLEX-0001");
		assertThat(result.created()).isEqualTo(1);
	}

	@Test
	void resolve_shouldNotSaveWhenDisplayInfoIsUnchanged() {
		when(store.findByAptSeqIn(any())).thenReturn(Map.of("11680-283", existing()));

		ComplexResolution result = logic.resolve("11680", Map.of("11680-283", latest));

		verify(store, never()).save(any());
		verify(idGenerator, never()).nextDomainId(any());
		assertThat(result.created()).isZero();
		assertThat(result.refreshed()).isZero();
		assertThat(result.complexIdByAptSeq()).containsEntry("11680-283", "HOUSE_COMPLEX-0001");
	}

	@Test
	void resolve_shouldSaveWhenNameChangedAndKeepStatuses() {
		when(store.findByAptSeqIn(any())).thenReturn(Map.of("11680-283", existing()));
		TradeRecord renamed = new TradeRecord(latest.aptSeq(), latest.lawdCd(), latest.umdCd(), latest.umdName(),
			latest.bonbun(), latest.bubun(), "개포우성3차 새이름", latest.roadName(), latest.roadBonbun(),
			latest.roadBubun(), latest.exclusiveArea(), latest.amountManwon(), latest.dealDate(), latest.floor(),
			latest.aptDong(), latest.buildYear(), false, null, latest.dealingType());

		ComplexResolution result = logic.resolve("11680", Map.of("11680-283", renamed));

		ArgumentCaptor<HouseComplex> saved = ArgumentCaptor.forClass(HouseComplex.class);
		verify(store).save(saved.capture());
		assertThat(saved.getValue().getAptName()).isEqualTo("개포우성3차 새이름");
		assertThat(saved.getValue().getGeocodeStatus()).isEqualTo(GeocodeStatus.DONE);
		assertThat(result.refreshed()).isEqualTo(1);
	}

	@Test
	void resolve_shouldNotEraseExistingValuesWithBlankOnes() {
		when(store.findByAptSeqIn(any())).thenReturn(Map.of("11680-283", existing()));
		TradeRecord sparse = new TradeRecord(latest.aptSeq(), latest.lawdCd(), null, null, null, null,
			latest.aptName(), null, null, null, latest.exclusiveArea(), latest.amountManwon(), latest.dealDate(),
			null, null, null, false, null, latest.dealingType());

		logic.resolve("11680", Map.of("11680-283", sparse));

		verify(store, never()).save(any());
	}
}
