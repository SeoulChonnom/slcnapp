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
import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.aggregate.house.logic.HouseRedevelopmentLogic;
import com.seoulchonnom.aggregate.house.logic.RedevelopmentReplaceResult;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentRow;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;
import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteRedevelopmentListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteRedevelopmentRdo;
import com.seoulchonnom.spec.house.facade.sdo.RedevelopmentImportRdo;
import com.seoulchonnom.spec.house.mapper.HouseAdminMapperImpl;

class HouseAdminFlowTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

	private final HouseComplexLogic complexLogic = mock(HouseComplexLogic.class);
	private final HouseRedevelopmentLogic redevelopmentLogic = mock(HouseRedevelopmentLogic.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));
	private final HouseAdminFlow flow = new HouseAdminFlow(complexLogic, redevelopmentLogic, new HouseAdminMapperImpl(), clock);

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

	private static final String CSV_HEADER = "CODE,순번,자치구,구역명,지번주소,도로명주소,공공/민간,일반/재촉,사업유형,추진단계,좌표검색주소(선택)";

	private static byte[] csv(String... lines) {
		return String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	void importRedevelopments_shouldReplaceWithParsedRowsAndReturnCounts() {
		when(redevelopmentLogic.replaceAll(any(), eq(TODAY)))
			.thenReturn(new RedevelopmentReplaceResult(1, 1, 0, 0, 2, 1));

		RedevelopmentImportRdo result = flow.importRedevelopments(csv(CSV_HEADER,
			"1460,1,종로구,수송1-2,수송동146-12,종로1길36,민간,일반,도시정비형 재개발,건축심의,", ",,,,,,,,,,"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RedevelopmentRow>> rows = ArgumentCaptor.forClass(List.class);
		verify(redevelopmentLogic).replaceAll(rows.capture(), eq(TODAY));
		assertThat(rows.getValue()).extracting(RedevelopmentRow::code).containsExactly("1460");
		assertThat(result.getTotal()).isEqualTo(1);
		assertThat(result.getInserted()).isEqualTo(1);
		assertThat(result.getDeleted()).isEqualTo(2);
		assertThat(result.getSkippedRows()).isEqualTo(1);
		assertThat(result.getGeocodePending()).isEqualTo(1);
	}

	@Test
	void importRedevelopments_shouldStoreNothingWhenAnyRowIsInvalid() {
		assertThatThrownBy(() -> flow.importRedevelopments(csv(CSV_HEADER,
			"1460,1,종로구,수송1-2,수송동146-12,종로1길36,민간,일반,도시정비형 재개발,건축심의,",
			"1462,2,없는구,수송1-7,수송동80,종로5길68,민간,일반,도시정비형 재개발,사업시행,")))
			.isInstanceOf(HouseValidationException.class);

		verifyNoInteractions(redevelopmentLogic);
	}

	@Test
	void importRedevelopments_shouldRejectEmptyAndOversizedFiles() {
		assertThatThrownBy(() -> flow.importRedevelopments(new byte[0])).isInstanceOf(HouseValidationException.class);
		assertThatThrownBy(() -> flow.importRedevelopments(new byte[HouseAdminFlow.MAX_REDEVELOPMENT_CSV_BYTES + 1]))
			.isInstanceOf(HouseValidationException.class);
		verifyNoInteractions(redevelopmentLogic);
	}

	@Test
	void getIncompleteRedevelopments_shouldMapRowsAndUseZeroBasedPageForTheStore() {
		HouseRedevelopment redevelopment = HouseRedevelopment.builder().sourceKey("1460").name("수송1-2")
			.lawdCd("11110").jibunAddress("수송동146-12").roadAddress("종로1길36").geocodeStatus(GeocodeStatus.FAILED)
			.geocodeAttempts(0).build();
		when(redevelopmentLogic.findIncomplete(2, 20)).thenReturn(List.of(redevelopment));
		when(redevelopmentLogic.countIncomplete()).thenReturn(41L);

		IncompleteRedevelopmentListRdo result = flow.getIncompleteRedevelopments(3, null);

		assertThat(result.getTotalCount()).isEqualTo(41);
		IncompleteRedevelopmentRdo item = result.getItems().get(0);
		assertThat(item.getCode()).isEqualTo("1460");
		assertThat(item.getDistrictName()).isEqualTo("종로구");
		assertThat(item.getGeocodeStatus()).isEqualTo("FAILED");
		assertThat(item.getGeocodeQuery()).isNull();
	}

	@Test
	void getIncompleteRedevelopments_shouldRejectPageBelowOne() {
		assertThatThrownBy(() -> flow.getIncompleteRedevelopments(0, null)).isInstanceOf(BusinessException.class);
	}
}
