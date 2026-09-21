package com.seoulchonnom.spec.inspection.facade.sdo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 필터 칩 카운트. keyword/revisitIntent/page 어느 것에도 영향받지 않는 전역 집계다
 * (hidden=false인 전 지역이 모수). 최신 회차 기준으로 센다.
 *
 * total은 지역 총 수이고 yes+maybe+no와 다를 수 있다 — 임장이 0건이거나, 있어도 최신 회차의
 * revisitIntent가 아직 null(DRAFT 상태 등)인 지역은 어느 칸에도 잡히지 않기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevisitIntentCountsRdo {
	private long total;
	@JsonProperty("YES")
	private long yes;
	@JsonProperty("MAYBE")
	private long maybe;
	@JsonProperty("NO")
	private long no;
}
