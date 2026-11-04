package com.seoulchonnom.aggregate.house.redevelopment;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentCsvParser.CsvFormatException;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentCsvParser.CsvRecord;

class RedevelopmentCsvParserTest {
	@Test
	void parse_shouldStripBomAndSplitCells() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("﻿CODE,순번\n1460,1\n");

		assertThat(records).hasSize(2);
		assertThat(records.get(0).cells()).containsExactly("CODE", "순번");
		assertThat(records.get(1).cells()).containsExactly("1460", "1");
	}

	@Test
	void parse_shouldKeepCommaInsideQuotes() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("2494,26,중구,\"마포로5-10,11\",중림동157-2\n");

		assertThat(records.get(0).cells()).containsExactly("2494", "26", "중구", "마포로5-10,11", "중림동157-2");
	}

	@Test
	void parse_shouldUnescapeDoubledQuotes() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("a,\"그는 \"\"구역\"\"이라 했다\",c\n");

		assertThat(records.get(0).cells()).containsExactly("a", "그는 \"구역\"이라 했다", "c");
	}

	@Test
	void parse_shouldReadCrlfAndLfLineEndings() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("a,b\r\nc,d\ne,f\r\n");

		assertThat(records).extracting(CsvRecord::cells)
			.containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
		assertThat(records).extracting(CsvRecord::line).containsExactly(1, 2, 3);
	}

	@Test
	void parse_shouldKeepNewlineInsideQuotesAndCountLinesAfterIt() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("a,\"첫째\n둘째\",c\nx,y,z\n");

		assertThat(records.get(0).cells()).containsExactly("a", "첫째\n둘째", "c");
		assertThat(records.get(0).line()).isEqualTo(1);
		assertThat(records.get(1).line()).isEqualTo(3);
	}

	@Test
	void parse_shouldKeepEmptyCellsAndBlankLines() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("a,,c,\n\nx\n");

		assertThat(records.get(0).cells()).containsExactly("a", "", "c", "");
		assertThat(records.get(1).cells()).containsExactly("");
		assertThat(records.get(2).cells()).containsExactly("x");
	}

	@Test
	void parse_shouldReadLastRecordWithoutTrailingNewline() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("a,b\nc,d");

		assertThat(records).hasSize(2);
		assertThat(records.get(1).cells()).containsExactly("c", "d");
	}

	@Test
	void parse_shouldReturnNoRecordsForEmptyInput() {
		assertThat(RedevelopmentCsvParser.parse("")).isEmpty();
		assertThat(RedevelopmentCsvParser.parse("﻿")).isEmpty();
	}

	@Test
	void parse_shouldTreatQuoteInTheMiddleOfAFieldAsLiteral() {
		List<CsvRecord> records = RedevelopmentCsvParser.parse("5\"형,b\n");

		assertThat(records.get(0).cells()).containsExactly("5\"형", "b");
	}

	@Test
	void parse_shouldRejectUnclosedQuote() {
		assertThatThrownBy(() -> RedevelopmentCsvParser.parse("a,\"열린 따옴표\nb,c\n"))
			.isInstanceOf(CsvFormatException.class).extracting("line").isEqualTo(1);
	}
}
