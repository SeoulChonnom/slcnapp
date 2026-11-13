package com.seoulchonnom.aggregate.house.collect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * aptSeq가 비어 온 행의 대체 키를 만들 때 쓰는 단지명 정규화.
 * 공백 제거, 괄호와 그 안 내용 제거, "아파트" 제거, 영문 소문자화.
 */
public final class ComplexNameNormalizer {
	private ComplexNameNormalizer() {
	}

	public static String normalize(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll("\\([^)]*\\)", "")
			.replaceAll("\\s+", "")
			.replace("아파트", "")
			.toLowerCase(Locale.ROOT);
	}

	/**
	 * ALT:{sggCd}{umdCd}{bonbun}{bubun}:{정규화명 sha256 앞 8자}
	 */
	public static String alternativeAptSeq(String lawdCd, String umdCd, String bonbun, String bubun, String name) {
		String hash = sha256Hex(normalize(name)).substring(0, 8);
		return "ALT:" + nullToEmpty(lawdCd) + nullToEmpty(umdCd) + nullToEmpty(bonbun) + nullToEmpty(bubun) + ":" + hash;
	}

	static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256은 모든 JVM이 제공한다.
			throw new IllegalStateException(e);
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
