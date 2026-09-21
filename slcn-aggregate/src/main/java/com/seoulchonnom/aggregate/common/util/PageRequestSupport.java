package com.seoulchonnom.aggregate.common.util;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

/**
 * 지역 목록·임장 목록이 공유하는 page/size 정규화 규칙.
 *
 * size가 상한을 넘거나 0 이하로 들어오면 400 대신 기본값/상한으로 조용히 보정한다 —
 * 화면이 조작할 일 없는 값으로 사용자를 막을 이유가 없다. page만 음수를 명백한 클라이언트
 * 오류로 보고 400을 낸다.
 */
public final class PageRequestSupport {
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private PageRequestSupport() {
	}

	public static int normalizePage(int page) {
		if (page < 0) {
			throw new BadRequestException("page는 0 이상이어야 합니다. page=" + page);
		}
		return page;
	}

	/**
	 * size가 없거나(null) 0 이하면 기본값 20으로, 상한(100)을 넘으면 100으로 깎는다.
	 */
	public static int normalizeSize(Integer size) {
		if (size == null || size <= 0) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}

	public static int offsetOf(int page, int size) {
		return page * size;
	}
}
