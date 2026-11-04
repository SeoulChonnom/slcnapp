package com.seoulchonnom.aggregate.house.collect;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 거래 중복 제거 키. 원천에 거래 고유 ID가 없어서 (aptSeq, aptDong, floor, 전용면적, 계약일, 금액)과
 * 같은 응답 안의 출현 순번으로 만든다. 같은 날 같은 동·층·면적·가격 거래가 실제로 두 건일 때
 * 하나로 합쳐지지 않게 하려는 것이다.
 * 해제 여부·거래 유형은 키에 넣지 않는다. 해제되면 같은 행이 갱신되어야 한다.
 *
 * 키 형식을 바꾸면 기존 거래 전부가 새 거래로 들어가므로 바꾸지 않는다.
 */
public final class DedupKeyFactory {
	private DedupKeyFactory() {
	}

	/**
	 * 응답 순서대로 레코드마다 키를 하나씩 돌려준다. 레코드의 aptSeq는 이미 단지 키로 확정된 값이어야 한다.
	 */
	public static List<String> create(List<TradeRecord> records) {
		Map<String, Integer> occurrences = new HashMap<>();
		List<String> keys = new ArrayList<>(records.size());
		for (TradeRecord record : records) {
			String base = String.join("|",
				nullToEmpty(record.aptSeq()),
				nullToEmpty(record.aptDong()),
				record.floor() == null ? "" : String.valueOf(record.floor()),
				record.exclusiveArea() == null ? "" : record.exclusiveArea().setScale(2, RoundingMode.HALF_UP).toPlainString(),
				record.dealDate() == null ? "" : record.dealDate().toString(),
				String.valueOf(record.amountManwon()));
			int sequence = occurrences.merge(base, 1, Integer::sum);
			keys.add(ComplexNameNormalizer.sha256Hex(base + "|" + sequence));
		}
		return keys;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
