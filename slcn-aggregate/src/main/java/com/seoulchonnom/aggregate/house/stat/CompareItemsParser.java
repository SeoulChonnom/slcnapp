package com.seoulchonnom.aggregate.house.stat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.seoulchonnom.aggregate.house.exception.HouseValidationException;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

/**
 * 비교 요청 items("{complexId}:B59,{complexId}:B84")를 해석하고 검증한다. 순수 클래스다.
 * 위반은 처음 하나에서 멈추지 않고 모두 모아 errors[]로 낸다(field = items[n], code = FORMAT|TOO_MANY|DUPLICATED|INVALID_AREA).
 * complexId에는 ':'와 ','가 들어가지 않으므로 항목은 ','로, 단지와 구간은 마지막 ':'로 나눈다.
 */
public final class CompareItemsParser {
	public static final int MAX_ITEMS = 3;

	private CompareItemsParser() {
	}

	public static List<CompareItem> parse(String items) {
		if (items == null || items.isBlank()) {
			throw fail(List.of(new Violation("items", "FORMAT", "items는 {complexId}:B59 형식으로 1개 이상 필요합니다.")));
		}
		List<Violation> violations = new ArrayList<>();
		List<CompareItem> parsed = new ArrayList<>();
		Set<CompareItem> seen = new HashSet<>();
		// limit -1: 끝의 쉼표가 만든 빈 항목도 버리지 않고 FORMAT 위반으로 잡는다.
		String[] tokens = items.split(",", -1);
		for (int index = 0; index < tokens.length; index++) {
			String field = "items[" + index + "]";
			if (index >= MAX_ITEMS) {
				violations.add(new Violation(field, "TOO_MANY", "비교는 최대 " + MAX_ITEMS + "개까지 가능합니다."));
				continue;
			}
			String token = tokens[index].strip();
			int colon = token.lastIndexOf(':');
			if (colon < 0) {
				violations.add(new Violation(field, "FORMAT", "{complexId}:B59 또는 {complexId}:B84 형식이어야 합니다."));
				continue;
			}
			String complexId = token.substring(0, colon).strip();
			String areaText = token.substring(colon + 1).strip();
			if (complexId.isEmpty()) {
				violations.add(new Violation(field, "FORMAT", "complexId가 비어 있습니다."));
				continue;
			}
			AreaBucket area = toArea(areaText);
			if (area == null) {
				violations.add(new Violation(field, "INVALID_AREA", "면적 구간은 B59 또는 B84여야 합니다."));
				continue;
			}
			CompareItem item = new CompareItem(complexId, area);
			if (!seen.add(item)) {
				violations.add(new Violation(field, "DUPLICATED", "같은 단지·구간이 중복되었습니다."));
				continue;
			}
			parsed.add(item);
		}
		if (!violations.isEmpty()) {
			throw fail(violations);
		}
		return parsed;
	}

	private static AreaBucket toArea(String text) {
		return switch (text) {
			case "B59" -> AreaBucket.B59;
			case "B84" -> AreaBucket.B84;
			default -> null;
		};
	}

	private static HouseValidationException fail(List<Violation> violations) {
		return new HouseValidationException(violations.get(0).message(), violations);
	}
}
