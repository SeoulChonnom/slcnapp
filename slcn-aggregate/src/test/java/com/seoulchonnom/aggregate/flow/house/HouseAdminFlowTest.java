package com.seoulchonnom.aggregate.flow.house;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;
import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;
import com.seoulchonnom.spec.house.mapper.HouseAdminMapperImpl;

class HouseAdminFlowTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

	private final HouseComplexLogic complexLogic = mock(HouseComplexLogic.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
	private final HouseAdminFlow flow = new HouseAdminFlow(complexLogic, new HouseAdminMapperImpl(), clock);

	private HouseComplex complex() {
		HouseComplex complex = HouseComplex.builder().aptSeq("11230-123").lawdCd("11230").umdName("전농동")
			.bonbun("0123").bubun("0004").aptName("전농래미안").geocodeStatus(GeocodeStatus.FAILED)
			.registerStatus(RegisterStatus.DONE).addressOverride(new AddressOverride("서울 동대문구 x", "0123", null))
			.build();
		complex.setId("HOUSE_COMPLEX-0001");
		return complex;
	}

	@Test
	void getIncompleteComplexes_shouldMapRowsAndUseOneBasedPageOffset() {
		when(complexLogic.findIncomplete(TODAY, 20, 40)).thenReturn(List.of(new IncompleteComplexPdo(complex(), 7)));
		when(complexLogic.countIncomplete()).thenReturn(42L);

		IncompleteComplexListRdo result = flow.getIncompleteComplexes(3, null);

		assertThat(result.getTotalCount()).isEqualTo(42);
		assertThat(result.getItems()).hasSize(1);
		IncompleteComplexRdo item = result.getItems().get(0);
		assertThat(item.getComplexId()).isEqualTo("HOUSE_COMPLEX-0001");
		assertThat(item.getAptSeq()).isEqualTo("11230-123");
		assertThat(item.getName()).isEqualTo("전농래미안");
		assertThat(item.getDistrictName()).isEqualTo("동대문구");
		assertThat(item.getUmdName()).isEqualTo("전농동");
		assertThat(item.getBonbun()).isEqualTo("123");
		assertThat(item.getBubun()).isEqualTo("4");
		assertThat(item.getGeocodeStatus()).isEqualTo("FAILED");
		assertThat(item.getRegisterStatus()).isEqualTo("DONE");
		assertThat(item.getTrades12m()).isEqualTo(7);
		assertThat(item.getAddressOverride().getBonbun()).isEqualTo("123");
		assertThat(item.getAddressOverride().getBubun()).isNull();
	}

	@Test
	void getIncompleteComplexes_shouldDefaultToFirstPageAndClampSize() {
		when(complexLogic.findIncomplete(any(), anyInt(), anyInt())).thenReturn(List.of());

		flow.getIncompleteComplexes(null, 1000);

		verify(complexLogic).findIncomplete(TODAY, 100, 0);
	}

	@Test
	void getIncompleteComplexes_shouldRejectPageBelowOne() {
		assertThatThrownBy(() -> flow.getIncompleteComplexes(0, 20)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void overrideAddress_shouldNormalizeAndDelegateAndAttachTradeCount() {
		when(complexLogic.overrideAddress(eq("HOUSE_COMPLEX-0001"), any())).thenReturn(complex());
		when(complexLogic.countTrades12m("HOUSE_COMPLEX-0001", TODAY)).thenReturn(9L);

		IncompleteComplexRdo result = flow.overrideAddress("HOUSE_COMPLEX-0001",
			new AddressOverrideUdo("  서울특별시 동대문구 왕산로 1  ", "12", "3"));

		ArgumentCaptor<AddressOverride> captor = ArgumentCaptor.forClass(AddressOverride.class);
		verify(complexLogic).overrideAddress(eq("HOUSE_COMPLEX-0001"), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new AddressOverride("서울특별시 동대문구 왕산로 1", "0012", "0003"));
		assertThat(result.getTrades12m()).isEqualTo(9);
	}

	@Test
	void overrideAddress_shouldAcceptBonbunOnly() {
		when(complexLogic.overrideAddress(any(), any())).thenReturn(complex());

		flow.overrideAddress("c", new AddressOverrideUdo(null, "652", null));

		verify(complexLogic).overrideAddress("c", new AddressOverride(null, "0652", null));
	}

	@Test
	void overrideAddress_shouldPropagateNotFound() {
		when(complexLogic.overrideAddress(any(), any())).thenThrow(new HouseComplexNotFoundException());

		assertThatThrownBy(() -> flow.overrideAddress("nope", new AddressOverrideUdo("주소", null, null)))
			.isInstanceOf(HouseComplexNotFoundException.class);
	}

	private void assertInvalid(AddressOverrideUdo request) {
		assertThatThrownBy(() -> flow.overrideAddress("c", request)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(complexLogic, never()).overrideAddress(any(), any());
	}

	@Test
	void overrideAddress_shouldRejectEmptyBody() {
		assertInvalid(new AddressOverrideUdo(null, null, null));
		assertInvalid(null);
	}

	@Test
	void overrideAddress_shouldRejectBlankOrTooLongRoadAddress() {
		assertInvalid(new AddressOverrideUdo("   ", null, null));
		assertInvalid(new AddressOverrideUdo("가".repeat(201), null, null));
	}

	@Test
	void overrideAddress_shouldAcceptRoadAddressOfExactlyTwoHundredChars() {
		when(complexLogic.overrideAddress(any(), any())).thenReturn(complex());

		flow.overrideAddress("c", new AddressOverrideUdo("가".repeat(200), null, null));

		verify(complexLogic).overrideAddress(eq("c"), any());
	}

	@Test
	void overrideAddress_shouldRejectNonNumericOrTooLongLotNumbers() {
		assertInvalid(new AddressOverrideUdo(null, "12a", null));
		assertInvalid(new AddressOverrideUdo(null, "12345", null));
		assertInvalid(new AddressOverrideUdo(null, "", null));
		assertInvalid(new AddressOverrideUdo(null, "12", "-1"));
	}

	@Test
	void overrideAddress_shouldRejectBubunWithoutBonbun() {
		assertInvalid(new AddressOverrideUdo("주소", null, "3"));
	}
}
