package com.seoulchonnom.aggregate.house.logic;

import static com.seoulchonnom.aggregate.house.collect.TradeRecords.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.collect.RegisterSummary;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.FarSource;
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

	private HouseComplex pending() {
		HouseComplex complex = existing();
		complex.setGeocodeStatus(GeocodeStatus.PENDING);
		complex.setRegisterStatus(RegisterStatus.PENDING);
		return complex;
	}

	private void saveReturnsArgument() {
		when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void applyGeocode_shouldStoreCoordinateAndRoadAddressAndMarkDone() {
		saveReturnsArgument();
		HouseComplex complex = pending();
		complex.setGeocodeAttempts(2);

		logic.applyGeocode(complex, GeocodeResult.found(37.4891, 127.0577, "서울특별시 강남구 개포로 307"));

		assertThat(complex.getLatitude()).isEqualTo(37.4891);
		assertThat(complex.getLongitude()).isEqualTo(127.0577);
		assertThat(complex.getRoadAddress()).isEqualTo("서울특별시 강남구 개포로 307");
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.DONE);
		assertThat(complex.getGeocodeAttempts()).isZero();
		verify(store).save(complex);
	}

	@Test
	void applyGeocode_shouldFailImmediatelyWhenNotFound() {
		saveReturnsArgument();
		HouseComplex complex = pending();

		logic.applyGeocode(complex, GeocodeResult.of(GeocodeResult.Outcome.NOT_FOUND));

		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
		assertThat(complex.getLatitude()).isNull();
	}

	@Test
	void applyGeocode_shouldKeepPendingUntilThirdUpstreamFailure() {
		saveReturnsArgument();
		HouseComplex complex = pending();
		GeocodeResult upstream = GeocodeResult.of(GeocodeResult.Outcome.UPSTREAM_FAILED);

		logic.applyGeocode(complex, upstream);
		logic.applyGeocode(complex, upstream);
		assertThat(complex.getGeocodeAttempts()).isEqualTo(2);
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);

		logic.applyGeocode(complex, upstream);
		assertThat(complex.getGeocodeAttempts()).isEqualTo(3);
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
	}

	@Test
	void applyGeocode_shouldRejectOutcomesThatAreNotStored() {
		assertThatThrownBy(() -> logic.applyGeocode(pending(), GeocodeResult.of(GeocodeResult.Outcome.BUDGET_EXHAUSTED)))
			.isInstanceOf(IllegalArgumentException.class);
		verify(store, never()).save(any());
	}

	@Test
	void applyRegister_shouldStoreFieldsAndMarkDone() {
		saveReturnsArgument();
		HouseComplex complex = pending();
		complex.setRegisterAttempts(1);
		RegisterSummary summary = new RegisterSummary(405, new BigDecimal("249.50"), new BigDecimal("18.20"),
			LocalDate.of(1984, 8, 31), FarSource.TITLE);

		logic.applyRegister(complex, RegisterLookupResult.found(summary), LocalDate.of(2026, 11, 3));

		assertThat(complex.getHouseholds()).isEqualTo(405);
		assertThat(complex.getFarPct()).isEqualByComparingTo("249.50");
		assertThat(complex.getBcrPct()).isEqualByComparingTo("18.20");
		assertThat(complex.getApprovalDate()).isEqualTo(LocalDate.of(1984, 8, 31));
		assertThat(complex.getFarSource()).isEqualTo(FarSource.TITLE);
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.DONE);
		assertThat(complex.getRegisterAttempts()).isZero();
		assertThat(complex.getRegisterSyncedDate()).isEqualTo(LocalDate.of(2026, 11, 3));
	}

	@Test
	void applyRegister_shouldFailImmediatelyWhenNotFoundForNewComplex() {
		saveReturnsArgument();
		HouseComplex complex = pending();

		logic.applyRegister(complex, RegisterLookupResult.of(RegisterLookupResult.Outcome.NOT_FOUND),
			LocalDate.of(2026, 11, 3));

		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.FAILED);
	}

	@Test
	void applyRegister_shouldKeepPendingUntilThirdFailedCall() {
		saveReturnsArgument();
		HouseComplex complex = pending();
		RegisterLookupResult failed = RegisterLookupResult.of(RegisterLookupResult.Outcome.FAILED);
		LocalDate today = LocalDate.of(2026, 11, 3);

		logic.applyRegister(complex, failed, today);
		logic.applyRegister(complex, failed, today);
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.PENDING);
		assertThat(complex.getRegisterAttempts()).isEqualTo(2);

		logic.applyRegister(complex, failed, today);
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.FAILED);
	}

	@Test
	void applyRegister_shouldKeepExistingValuesWhenRenewalFailsOrFindsNothing() {
		saveReturnsArgument();
		HouseComplex complex = existing();
		complex.setHouseholds(405);
		complex.setRegisterSyncedDate(LocalDate.of(2026, 9, 1));

		HouseComplex afterFailure = logic.applyRegister(complex,
			RegisterLookupResult.of(RegisterLookupResult.Outcome.FAILED), LocalDate.of(2026, 11, 3));
		assertThat(afterFailure.getRegisterSyncedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
		verify(store, never()).save(any());

		logic.applyRegister(complex, RegisterLookupResult.of(RegisterLookupResult.Outcome.NOT_FOUND),
			LocalDate.of(2026, 11, 3));
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.DONE);
		assertThat(complex.getHouseholds()).isEqualTo(405);
		assertThat(complex.getRegisterSyncedDate()).isEqualTo(LocalDate.of(2026, 11, 3));
	}

	@Test
	void overrideAddress_shouldReplaceOverrideAndResetGeocodeWhenRoadAddressGiven() {
		saveReturnsArgument();
		HouseComplex complex = existing();
		complex.setGeocodeStatus(GeocodeStatus.FAILED);
		complex.setGeocodeAttempts(3);
		complex.setAddressOverride(new AddressOverride(null, "0100", "0001"));
		when(store.findById("HOUSE_COMPLEX-0001")).thenReturn(Optional.of(complex));

		logic.overrideAddress("HOUSE_COMPLEX-0001", new AddressOverride("서울특별시 강남구 개포로 310", null, null));

		assertThat(complex.getAddressOverride()).isEqualTo(new AddressOverride("서울특별시 강남구 개포로 310", null, null));
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
		assertThat(complex.getGeocodeAttempts()).isZero();
		// 지번은 보정하지 않았으므로 대장 상태는 그대로다.
		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.DONE);
	}

	@Test
	void overrideAddress_shouldResetBothRegisterAndGeocodeWhenBonbunGiven() {
		saveReturnsArgument();
		HouseComplex complex = existing();
		complex.setRegisterStatus(RegisterStatus.FAILED);
		complex.setRegisterAttempts(3);
		when(store.findById("HOUSE_COMPLEX-0001")).thenReturn(Optional.of(complex));

		logic.overrideAddress("HOUSE_COMPLEX-0001", new AddressOverride(null, "0653", "0001"));

		assertThat(complex.getRegisterStatus()).isEqualTo(RegisterStatus.PENDING);
		assertThat(complex.getRegisterAttempts()).isZero();
		assertThat(complex.getGeocodeStatus()).isEqualTo(GeocodeStatus.PENDING);
	}

	@Test
	void overrideAddress_shouldThrowNotFoundForUnknownComplex() {
		when(store.findById("nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> logic.overrideAddress("nope", new AddressOverride("x", null, null)))
			.isInstanceOf(HouseComplexNotFoundException.class);
		verify(store, never()).save(any());
	}
}
