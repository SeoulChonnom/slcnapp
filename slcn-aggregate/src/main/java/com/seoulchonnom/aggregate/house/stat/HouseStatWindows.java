package com.seoulchonnom.aggregate.house.stat;

import java.time.LocalDate;

/**
 * 통계 창의 날짜 경계. 모든 창은 왼쪽 열림·오른쪽 닫힘 (start, end]이고 deal_date로 비교한다.
 * 말일 보정(05-31 − 3개월 = 02-28)은 LocalDate.minusMonths의 기본 동작을 따른다. 날짜 계산은 이 클래스 한 곳에서만 한다.
 *
 * @param w3mStart 최근 3개월 (w3mStart, today]
 * @param w6mStart 최근 6개월
 * @param w12mStart 최근 12개월
 * @param w3m1yStart 1년 전 3개월 창 (w3m1yStart, w3m1yEnd]
 * @param w3m3yStart 3년 전 3개월 창 (w3m3yStart, w3m3yEnd]
 * @param outlierSince 이상치 판정 대상 거래일 하한(제외). 통계 최대 범위(39개월)보다 한 달 더 넓다
 */
public record HouseStatWindows(LocalDate today, LocalDate w3mStart, LocalDate w6mStart, LocalDate w12mStart,
							   LocalDate w3m1yStart, LocalDate w3m1yEnd, LocalDate w3m3yStart, LocalDate w3m3yEnd,
							   LocalDate outlierSince) {
	public static HouseStatWindows of(LocalDate today) {
		return new HouseStatWindows(today,
			today.minusMonths(3),
			today.minusMonths(6),
			today.minusMonths(12),
			today.minusMonths(15), today.minusMonths(12),
			today.minusMonths(39), today.minusMonths(36),
			today.minusMonths(40));
	}
}
