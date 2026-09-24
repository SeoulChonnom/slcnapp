package com.seoulchonnom.spec.common.response;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * offset 기반 목록 응답이 공유하는 계약. 지역 목록·임장 목록이 같은 문법을 쓴다.
 *
 * 커서 대신 offset을 쓰는 이유: 지역 목록은 정렬축이 3개(RECENT_VISIT/VISIT_COUNT/TOP_INTEREST)라
 * 커서가 정렬 키를 인코딩해야 해서 복잡해지고, 이 저장소의 데이터 규모가 작아 offset 성능 저하가
 * 문제될 정도가 아니다.
 *
 * 지역 목록처럼 페이지 계약에 필드를 더 얹어야 하면 이 클래스를 상속해서 확장한다
 * (예: InspectionAreaListRdo). 상속하면 JSON에 items/totalCount/hasNext가 그대로 평면으로
 * 나오면서 추가 필드만 옆에 붙는다 — 별도 래퍼로 감싸 중첩시키는 것보다 FE가 다루기 쉽다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageRdo<T> {
	private List<T> items = new ArrayList<>();
	private long totalCount;
	private boolean hasNext;

	public static <T> PageRdo<T> of(List<T> items, long totalCount, int page, int size) {
		boolean hasNext = (long)(page + 1) * size < totalCount;
		return new PageRdo<>(items == null ? new ArrayList<>() : new ArrayList<>(items), totalCount, hasNext);
	}
}
