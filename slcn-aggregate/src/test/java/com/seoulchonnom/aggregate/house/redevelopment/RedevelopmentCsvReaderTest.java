package com.seoulchonnom.aggregate.house.redevelopment;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentCsvReader.ReadResult;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;

class RedevelopmentCsvReaderTest {
	private static final String HEADER = "CODE,순번,자치구,구역명,지번주소,도로명주소,공공/민간,일반/재촉,사업유형,추진단계,좌표검색주소(선택)";
	private static final String ROW = "1460,1,종로구,수송1-2,수송동146-12,종로1길36,민간,일반,도시정비형 재개발,건축심의,";

	private static byte[] csv(String... lines) {
		return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
	}

	private static List<Violation> errorsOf(byte[] content) {
		HouseValidationException e = catchThrowableOfType(HouseValidationException.class,
			() -> RedevelopmentCsvReader.read(content));
		assertThat(e).isNotNull();
		return e.getViolations();
	}

	@Test
	void read_shouldParseRowsWithBomAndNormalizeValues() {
		byte[] content = ("﻿" + HEADER + "\n" + ROW + "\n"
			+ "2494,26,중구,\"마포로5-10,11\",중림동157-2,-,민간,일반,주택정비형재개발,관리 처분,\"서울특별시 중구 서소문로 26\"\n")
			.getBytes(StandardCharsets.UTF_8);

		ReadResult result = RedevelopmentCsvReader.read(content);

		assertThat(result.skippedRows()).isZero();
		assertThat(result.rows()).hasSize(2);
		RedevelopmentRow first = result.rows().get(0);
		assertThat(first.code()).isEqualTo("1460");
		assertThat(first.lawdCd()).isEqualTo("11110");
		assertThat(first.name()).isEqualTo("수송1-2");
		assertThat(first.jibunAddress()).isEqualTo("수송동146-12");
		assertThat(first.roadAddress()).isEqualTo("종로1길36");
		assertThat(first.projectType()).isEqualTo("도시정비형 재개발");
		assertThat(first.stage()).isEqualTo("건축심의");
		assertThat(first.geocodeQuery()).isNull();
		RedevelopmentRow second = result.rows().get(1);
		assertThat(second.name()).isEqualTo("마포로5-10,11");
		assertThat(second.lawdCd()).isEqualTo("11140");
		assertThat(second.roadAddress()).isNull();
		assertThat(second.projectType()).isEqualTo("주택정비형 재개발");
		assertThat(second.stage()).isEqualTo("관리처분");
		assertThat(second.geocodeQuery()).isEqualTo("서울특별시 중구 서소문로 26");
	}

	@Test
	void read_shouldAcceptHeaderWithoutEleventhColumnAndRowsWithTenCells() {
		ReadResult result = RedevelopmentCsvReader.read(csv(HEADER.substring(0, HEADER.lastIndexOf(',')),
			"1460,1,종로구,수송1-2,수송동146-12,종로1길36,민간,일반,도시정비형 재개발,건축심의"));

		assertThat(result.rows()).hasSize(1);
		assertThat(result.rows().get(0).geocodeQuery()).isNull();
	}

	@Test
	void read_shouldNormalizeProjectTypeSpacingVariants() {
		String[] variants = {"공동주택재건축", "공동주택 재건축", " 공동 주택재건축 ", "아파트지구재건축", "단독주택 재건축", "도시정비형재개발"};
		for (String variant : variants) {
			ReadResult result = RedevelopmentCsvReader.read(
				csv(HEADER, "1,1,강남구,A,역삼동1,-,민간,일반,\"" + variant + "\",사업시행,"));
			assertThat(result.rows().get(0).projectType()).contains(" ").doesNotStartWith(" ");
		}
	}

	@Test
	void read_shouldTreatDashAndBlankAsNull() {
		ReadResult result = RedevelopmentCsvReader.read(
			csv(HEADER, "1,1,강남구,A,역삼동1,-,민간,일반,공동주택재건축,착공,-"));

		assertThat(result.rows().get(0).roadAddress()).isNull();
		assertThat(result.rows().get(0).geocodeQuery()).isNull();
	}

	@Test
	void read_shouldSkipBlankRowsAndMixedHeaderRowsAndCountThem() {
		ReadResult result = RedevelopmentCsvReader.read(csv(HEADER, ROW, ",,,,,,,,,,", HEADER, "", "합계,,,,,,,,,,",
			"1462,2,종로구,수송1-7,수송동80,종로5길68,민간,일반,도시정비형 재개발,사업시행,"));

		assertThat(result.rows()).extracting(RedevelopmentRow::code).containsExactly("1460", "1462");
		assertThat(result.skippedRows()).isEqualTo(4);
	}

	@Test
	void read_shouldRejectHeaderMismatch() {
		List<Violation> errors = errorsOf(csv("CODE,순번,구,구역명,지번주소,도로명주소,공공/민간,일반/재촉,사업유형,추진단계", ROW));

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).field()).isEqualTo("line 1");
		assertThat(errors.get(0).code()).isEqualTo("HEADER_MISMATCH");
	}

	@Test
	void read_shouldRejectUnknownDistrictWithLineNumber() {
		List<Violation> errors = errorsOf(csv(HEADER, ROW,
			"1462,2,종로,수송1-7,수송동80,종로5길68,민간,일반,도시정비형 재개발,사업시행,"));

		assertThat(errors).extracting(Violation::field, Violation::code).containsExactly(tuple("line 3", "DISTRICT_INVALID"));
	}

	@Test
	void read_shouldRejectUnknownProjectTypeAndStage() {
		List<Violation> errors = errorsOf(csv(HEADER,
			"1,1,강남구,A,역삼동1,-,민간,일반,재개발,입주,"));

		assertThat(errors).extracting(Violation::code).containsExactly("PROJECT_TYPE_INVALID", "STAGE_INVALID");
	}

	@Test
	void read_shouldRejectMissingNameAndJibun() {
		List<Violation> errors = errorsOf(csv(HEADER,
			"1,1,강남구,-,,-,민간,일반,공동주택재건축,착공,"));

		assertThat(errors).extracting(Violation::code).containsExactly("NAME_REQUIRED", "JIBUN_REQUIRED");
	}

	@Test
	void read_shouldRejectDuplicatedCode() {
		List<Violation> errors = errorsOf(csv(HEADER, ROW, ROW));

		assertThat(errors).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("line 3", "CODE_DUPLICATED"));
	}

	@Test
	void read_shouldReportCsvLineNumberOfRowsAfterMultilineCell() {
		List<Violation> errors = errorsOf(csv(HEADER, "1,1,강남구,\"가\n나\",역삼동1,-,민간,일반,공동주택재건축,착공,",
			"2,2,없는구,B,역삼동2,-,민간,일반,공동주택재건축,착공,"));

		assertThat(errors).extracting(Violation::field).containsExactly("line 4");
	}

	@Test
	void read_shouldRejectMoreCellsThanTemplate() {
		List<Violation> errors = errorsOf(csv(HEADER,
			"1,1,강남구,A,B,역삼동1,-,민간,일반,공동주택재건축,착공,,여분"));

		assertThat(errors).extracting(Violation::code).contains("COLUMN_COUNT");
	}

	@Test
	void read_shouldCapListedErrorsAtFiftyButCountAll() {
		StringBuilder builder = new StringBuilder(HEADER).append('\n');
		for (int i = 1; i <= 60; i++) {
			builder.append(i).append(",1,없는구,A,역삼동1,-,민간,일반,공동주택재건축,착공,\n");
		}

		HouseValidationException e = catchThrowableOfType(HouseValidationException.class,
			() -> RedevelopmentCsvReader.read(builder.toString().getBytes(StandardCharsets.UTF_8)));

		assertThat(e.getViolations()).hasSize(RedevelopmentCsvReader.MAX_ERRORS);
		assertThat(e.getMessage()).contains("60건");
	}

	@Test
	void read_shouldRejectNonUtf8() {
		byte[] cp949 = (HEADER + "\n" + ROW).getBytes(Charset.forName("MS949"));

		assertThat(errorsOf(cp949)).extracting(Violation::code).containsExactly("ENCODING");
	}

	@Test
	void read_shouldRejectFileWithoutAnyDataRow() {
		assertThat(errorsOf(csv(HEADER))).extracting(Violation::code).containsExactly("EMPTY");
		assertThat(errorsOf(csv(""))).extracting(Violation::code).containsExactly("EMPTY");
	}

	@Test
	void read_shouldRejectUnclosedQuote() {
		assertThat(errorsOf(csv(HEADER, "1,1,강남구,\"A,역삼동1")))
			.extracting(Violation::code).containsExactly("CSV_FORMAT");
	}
}
