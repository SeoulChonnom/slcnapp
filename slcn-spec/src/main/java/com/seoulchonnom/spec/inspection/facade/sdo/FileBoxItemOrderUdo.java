package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사진 정렬 일괄 갱신. itemId가 FileBox 문서 안에서 유일하므로
 * 임장 사진과 매물 사진을 한 엔드포인트가 함께 처리한다 — targetType을 받지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileBoxItemOrderUdo {
	private String itemId;
	private int sortOrder;
}
