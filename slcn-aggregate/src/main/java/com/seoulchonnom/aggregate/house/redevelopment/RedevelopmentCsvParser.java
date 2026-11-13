package com.seoulchonnom.aggregate.house.redevelopment;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 CSV 파서. 따옴표 필드(안의 쉼표·줄바꿈, "" 이스케이프)와 CRLF/LF를 읽고 UTF-8 BOM을 없앤다. 순수 클래스다.
 * 관리자가 엑셀에서 "CSV UTF-8"로 저장한 파일만 읽으면 되므로 구분자는 쉼표로 고정한다.
 */
public final class RedevelopmentCsvParser {
	private RedevelopmentCsvParser() {
	}

	/**
	 * @param line 레코드가 시작한 줄 번호(1부터). 따옴표 안 줄바꿈이 있어도 시작 줄을 가리킨다.
	 */
	public record CsvRecord(int line, List<String> cells) {
	}

	/**
	 * 따옴표가 닫히지 않은 경우처럼 CSV 구조 자체가 깨진 입력.
	 */
	public static class CsvFormatException extends RuntimeException {
		private final int line;

		public CsvFormatException(int line, String message) {
			super(message);
			this.line = line;
		}

		public int line() {
			return line;
		}
	}

	public static List<CsvRecord> parse(String text) {
		String input = text.startsWith("﻿") ? text.substring(1) : text;
		List<CsvRecord> records = new ArrayList<>();
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		boolean cellStarted = false;
		int line = 1;
		int recordLine = 1;
		int length = input.length();
		for (int i = 0; i < length; i++) {
			char c = input.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < length && input.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					if (c == '\n') {
						line++;
					}
					cell.append(c);
				}
				continue;
			}
			if (c == '"' && !cellStarted) {
				// 필드 맨 앞의 따옴표만 따옴표 필드의 시작이다. 중간의 따옴표는 글자 그대로 둔다.
				quoted = true;
				cellStarted = true;
			} else if (c == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
				cellStarted = false;
			} else if (c == '\r' || c == '\n') {
				if (c == '\r' && i + 1 < length && input.charAt(i + 1) == '\n') {
					i++;
				}
				cells.add(cell.toString());
				records.add(new CsvRecord(recordLine, cells));
				cells = new ArrayList<>();
				cell.setLength(0);
				cellStarted = false;
				line++;
				recordLine = line;
			} else {
				cell.append(c);
				cellStarted = true;
			}
		}
		if (quoted) {
			throw new CsvFormatException(recordLine, "따옴표가 닫히지 않았습니다.");
		}
		// 마지막 줄 끝에 줄바꿈이 없는 경우. 줄바꿈으로 끝난 파일은 빈 레코드를 더 만들지 않는다.
		if (cellStarted || cell.length() > 0 || !cells.isEmpty()) {
			cells.add(cell.toString());
			records.add(new CsvRecord(recordLine, cells));
		}
		return records;
	}
}
