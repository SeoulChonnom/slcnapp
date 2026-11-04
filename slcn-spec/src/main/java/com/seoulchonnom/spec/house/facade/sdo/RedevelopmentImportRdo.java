package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 정비사업 CSV 등록 결과.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedevelopmentImportRdo {
	/** 파일에서 읽은 구역 수 */
	private int total;
	private int inserted;
	private int updated;
	private int unchanged;
	/** 파일에 없어서 지운 구역 수 */
	private int deleted;
	/** 빈 행·섞여 들어온 머리글 행 등 건너뛴 행 수 */
	private int skippedRows;
	/** 등록 뒤 좌표를 기다리는 구역 수 */
	private long geocodePending;
}
