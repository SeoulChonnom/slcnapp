package com.seoulchonnom.aggregate.house.redevelopment;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentCsvParser.CsvFormatException;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentCsvParser.CsvRecord;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.vo.RedevelopmentProjectType;
import com.seoulchonnom.spec.house.entity.vo.RedevelopmentStage;

/**
 * 정비사업 등록 CSV를 읽고 행마다 검증·정규화한다. 순수 클래스다.
 * 오류는 처음 하나에서 멈추지 않고 모아서(최대 MAX_ERRORS개를 담는다) 한 번에 돌려준다. 오류가 하나라도 있으면 예외다.
 *
 * 서울시 엑셀은 분기마다 표기(띄어쓰기)·빈 값("-")·데이터 사이 머리글 행이 흔들려서, 값은 여기서 정규화하고
 * CODE가 숫자가 아닌 행은 섞여 들어온 머리글로 보고 건너뛴다.
 */
public final class RedevelopmentCsvReader {
	public static final int MAX_ERRORS = 50;
	static final int MAX_NAME_LENGTH = 200;
	static final int MAX_ADDRESS_LENGTH = 300;
	/** 양식 머리글. 11번째 열(좌표검색주소)은 없는 파일도 받는다. */
	static final List<String> HEADER = List.of("CODE", "순번", "자치구", "구역명", "지번주소", "도로명주소", "공공/민간",
		"일반/재촉", "사업유형", "추진단계", "좌표검색주소(선택)");
	private static final int REQUIRED_COLUMNS = 10;

	private RedevelopmentCsvReader() {
	}

	/**
	 * @param rows        CODE 순서가 아니라 파일 순서
	 * @param skippedRows 빈 행과 섞여 들어온 머리글 행 등 건너뛴 행 수
	 */
	public record ReadResult(List<RedevelopmentRow> rows, int skippedRows) {
	}

	public static ReadResult read(byte[] content) {
		String text = decode(content);
		List<CsvRecord> records;
		try {
			records = RedevelopmentCsvParser.parse(text);
		} catch (CsvFormatException e) {
			throw fail(1, List.of(new Violation("line " + e.line(), "CSV_FORMAT", e.getMessage())));
		}
		int index = 0;
		while (index < records.size() && isBlank(records.get(index))) {
			index++;
		}
		if (index == records.size()) {
			throw fail(1, List.of(new Violation("file", "EMPTY", "머리글과 데이터가 없는 파일입니다.")));
		}
		CsvRecord header = records.get(index);
		if (!headerMatches(header)) {
			throw fail(1, List.of(new Violation("line " + header.line(), "HEADER_MISMATCH",
				"머리글이 양식과 다릅니다. 양식: " + String.join(",", HEADER))));
		}

		Errors errors = new Errors();
		List<RedevelopmentRow> rows = new ArrayList<>();
		Set<String> codes = new HashSet<>();
		int skipped = 0;
		for (int i = index + 1; i < records.size(); i++) {
			CsvRecord record = records.get(i);
			if (isBlank(record)) {
				skipped++;
				continue;
			}
			String code = cell(record, 0);
			if (!code.matches("\\d+")) {
				skipped++;
				continue;
			}
			RedevelopmentRow row = validate(record, code, codes, errors);
			if (row != null) {
				rows.add(row);
			}
		}
		if (errors.total == 0 && rows.isEmpty()) {
			errors.add("file", "EMPTY", "등록할 구역이 한 행도 없습니다.");
		}
		if (errors.total > 0) {
			throw fail(errors.total, errors.violations);
		}
		return new ReadResult(List.copyOf(rows), skipped);
	}

	private static RedevelopmentRow validate(CsvRecord record, String code, Set<String> codes, Errors errors) {
		String at = "line " + record.line();
		boolean valid = true;
		if (record.cells().size() > HEADER.size() && record.cells().subList(HEADER.size(), record.cells().size())
			.stream().anyMatch(extra -> !extra.isBlank())) {
			errors.add(at, "COLUMN_COUNT", "열이 양식(" + HEADER.size() + "개)보다 많습니다. 쉼표가 든 값은 따옴표로 감싸야 합니다.");
			valid = false;
		}
		if (!codes.add(code)) {
			errors.add(at, "CODE_DUPLICATED", "CODE가 파일 안에서 중복되었습니다. CODE=" + code);
			valid = false;
		}
		String districtName = text(cell(record, 2));
		String lawdCd = districtName == null ? null : HouseConstant.lawdCdOf(districtName);
		if (lawdCd == null) {
			errors.add(at, "DISTRICT_INVALID", "자치구가 서울 25개 구 이름이 아닙니다. 자치구=" + districtName);
			valid = false;
		}
		String name = text(cell(record, 3));
		if (name == null) {
			errors.add(at, "NAME_REQUIRED", "구역명이 필요합니다.");
			valid = false;
		} else if (name.length() > MAX_NAME_LENGTH) {
			errors.add(at, "TOO_LONG", "구역명은 " + MAX_NAME_LENGTH + "자 이하여야 합니다.");
			valid = false;
		}
		String jibun = text(cell(record, 4));
		if (jibun == null) {
			errors.add(at, "JIBUN_REQUIRED", "지번주소가 필요합니다.");
			valid = false;
		}
		String road = text(cell(record, 5));
		String query = text(cell(record, 10));
		for (String address : new String[] {jibun, road, query}) {
			if (address != null && address.length() > MAX_ADDRESS_LENGTH) {
				errors.add(at, "TOO_LONG", "주소는 " + MAX_ADDRESS_LENGTH + "자 이하여야 합니다.");
				valid = false;
				break;
			}
		}
		RedevelopmentProjectType type = RedevelopmentProjectType.parse(cell(record, 8)).orElse(null);
		if (type == null) {
			errors.add(at, "PROJECT_TYPE_INVALID", "사업유형을 알 수 없습니다. 사업유형=" + cell(record, 8));
			valid = false;
		}
		RedevelopmentStage stage = RedevelopmentStage.parse(cell(record, 9)).orElse(null);
		if (stage == null) {
			errors.add(at, "STAGE_INVALID", "추진단계를 알 수 없습니다. 추진단계=" + cell(record, 9));
			valid = false;
		}
		if (!valid) {
			return null;
		}
		return new RedevelopmentRow(code, lawdCd, name, type.label(), stage.label(), jibun, road, query);
	}

	private static boolean headerMatches(CsvRecord header) {
		List<String> cells = header.cells().stream().map(String::strip).toList();
		// 끝의 빈 열(쉼표만 남은 경우)은 없는 것으로 본다.
		int size = cells.size();
		while (size > REQUIRED_COLUMNS && cells.get(size - 1).isEmpty()) {
			size--;
		}
		if (size != REQUIRED_COLUMNS && size != HEADER.size()) {
			return false;
		}
		return cells.subList(0, size).equals(HEADER.subList(0, size));
	}

	/**
	 * UTF-8이 아니면 거절한다. 엑셀의 "CSV"(CP949)로 저장한 파일을 조용히 깨진 글자로 읽지 않으려는 것이다.
	 */
	private static String decode(byte[] content) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
		} catch (CharacterCodingException e) {
			throw fail(1, List.of(new Violation("file", "ENCODING",
				"UTF-8 파일이 아닙니다. 엑셀에서 'CSV UTF-8'로 저장해 주세요.")));
		}
	}

	private static boolean isBlank(CsvRecord record) {
		return record.cells().stream().allMatch(String::isBlank);
	}

	private static String cell(CsvRecord record, int column) {
		return column < record.cells().size() ? record.cells().get(column).strip() : "";
	}

	/**
	 * 연속 공백(셀 안 줄바꿈 포함)을 하나로 줄이고, 비었거나 "-"면 null이다.
	 */
	private static String text(String raw) {
		String normalized = raw.replaceAll("\\s+", " ").strip();
		return normalized.isEmpty() || normalized.equals("-") ? null : normalized;
	}

	private static HouseValidationException fail(int total, List<Violation> violations) {
		return new HouseValidationException("정비사업 CSV에 오류가 " + total + "건 있어 등록하지 않았습니다.", violations);
	}

	private static final class Errors {
		final List<Violation> violations = new ArrayList<>();
		int total;

		void add(String field, String code, String message) {
			total++;
			if (violations.size() < MAX_ERRORS) {
				violations.add(new Violation(field, code, message));
			}
		}
	}
}
