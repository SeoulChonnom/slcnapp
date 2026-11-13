package com.seoulchonnom.aggregate.house.stat;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

class CompareItemsParserTest {
	private static HouseValidationException failure(String items) {
		return catchThrowableOfType(HouseValidationException.class, () -> CompareItemsParser.parse(items));
	}

	@Test
	void parse_shouldSplitItemsInRequestOrder() {
		List<CompareItem> items = CompareItemsParser.parse("HOUSE_COMPLEX-0001:B59,HOUSE_COMPLEX-0002:B84,c9:B59");

		assertThat(items).containsExactly(new CompareItem("HOUSE_COMPLEX-0001", AreaBucket.B59),
			new CompareItem("HOUSE_COMPLEX-0002", AreaBucket.B84), new CompareItem("c9", AreaBucket.B59));
	}

	@Test
	void parse_shouldAllowSameComplexWithBothAreas() {
		assertThat(CompareItemsParser.parse("c1:B59,c1:B84")).containsExactly(new CompareItem("c1", AreaBucket.B59),
			new CompareItem("c1", AreaBucket.B84));
	}

	@Test
	void parse_shouldSplitAtTheLastColon() {
		assertThat(CompareItemsParser.parse("a:b:B84")).containsExactly(new CompareItem("a:b", AreaBucket.B84));
	}

	@Test
	void parse_shouldTrimSpacesAroundItems() {
		assertThat(CompareItemsParser.parse(" c1 : B59 , c2:B84 ")).containsExactly(
			new CompareItem("c1", AreaBucket.B59), new CompareItem("c2", AreaBucket.B84));
	}

	@Test
	void parse_shouldRejectMoreThanThreeItemsWithTooManyOnTheExtraOnes() {
		HouseValidationException e = failure("c1:B59,c2:B59,c3:B59,c4:B59,c5:B84");

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[3]", "TOO_MANY"), tuple("items[4]", "TOO_MANY"));
	}

	@Test
	void parse_shouldRejectDuplicateComplexAndAreaAtTheLaterIndex() {
		HouseValidationException e = failure("c1:B59,c2:B84,c1:B59");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[2]", "DUPLICATED"));
	}

	@Test
	void parse_shouldRejectUnknownAreaBucket() {
		HouseValidationException e = failure("c1:B60,c2:OTHER,c3:b59");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[0]", "INVALID_AREA"), tuple("items[1]", "INVALID_AREA"),
				tuple("items[2]", "INVALID_AREA"));
	}

	@Test
	void parse_shouldRejectEmptyAreaAfterColon() {
		HouseValidationException e = failure("c1:");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[0]", "INVALID_AREA"));
	}

	@Test
	void parse_shouldRejectItemWithoutColon() {
		HouseValidationException e = failure("c1B59");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[0]", "FORMAT"));
	}

	@Test
	void parse_shouldRejectEmptyComplexId() {
		HouseValidationException e = failure(":B59, :B84");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[0]", "FORMAT"), tuple("items[1]", "FORMAT"));
	}

	@Test
	void parse_shouldRejectEmptyItemFromTrailingComma() {
		HouseValidationException e = failure("c1:B59,");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[1]", "FORMAT"));
	}

	@Test
	void parse_shouldRejectNullOrBlankItems() {
		for (String items : new String[] {null, "", "   "}) {
			HouseValidationException e = failure(items);

			assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
				.containsExactly(tuple("items", "FORMAT"));
		}
	}

	@Test
	void parse_shouldCollectEveryViolationAndUseFirstMessageAsTitle() {
		HouseValidationException e = failure("c1B59,c2:B60,c2:B84");

		assertThat(e.getViolations()).extracting(Violation::field, Violation::code)
			.containsExactly(tuple("items[0]", "FORMAT"), tuple("items[1]", "INVALID_AREA"));
		assertThat(e.getMessage()).isEqualTo(e.getViolations().get(0).message());
	}
}
