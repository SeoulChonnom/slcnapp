package com.seoulchonnom.aggregate.flow.house;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.house.collect.CallBudget;
import com.seoulchonnom.aggregate.house.collect.ComplexGeocoder;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.logic.HouseRedevelopmentLogic;
import com.seoulchonnom.aggregate.house.logic.RedevelopmentLinkResult;
import com.seoulchonnom.spec.house.constant.HouseConstant;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 5단계 정비사업 동기화(REDEVELOPMENT_SYNC)의 본문. 정비사업 구역은 관리자가 CSV로 올리고, 이 단계는
 * 좌표 PENDING 구역을 행안부로 좌표화한 뒤 단지-정비사업 연결을 매번 다시 만든다.
 * 행안부 예산은 3단계와 하나를 나눠 쓴다. 같은 CallBudget을 넘겨받고, 이 단계가 쓴 호출 수(예산 객체의 증가분)를 detail.jusoCalls에 남긴다.
 * 트랜잭션을 걸지 않는다. 외부 호출은 트랜잭션 밖이고 결과 저장은 HouseRedevelopmentLogic이 구역 1건 단위로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedevelopmentSyncStep {
	static final String JUSO_CALLS = "jusoCalls";
	/** 행안부 upstream 실패가 이만큼 연속되면 그날 좌표 처리를 접는다(3단계와 같은 규칙). */
	static final int JUSO_UPSTREAM_STREAK_LIMIT = 3;

	private final ComplexGeocoder complexGeocoder;
	private final HouseRedevelopmentLogic houseRedevelopmentLogic;

	/**
	 * @param jusoBudget 3단계와 공유하는 행안부 예산
	 */
	public StepOutcome run(LocalDate today, CallBudget jusoBudget, StepProgress progress) {
		long total = houseRedevelopmentLogic.count();
		if (total == 0) {
			progress.put("reason", "NO_DATA");
			progress.put(JUSO_CALLS, 0);
			return StepOutcome.of(BatchRunStatus.SKIPPED);
		}
		int usedAtStart = jusoBudget.used();
		progress.put("redevelopments", total);
		progress.put("jusoUsedBeforeRun", jusoBudget.usedBefore() + usedAtStart);
		for (String key : List.of("geocodeTargets", "geocodeDone", "geocodeNotFound", "geocodeUpstreamFailed",
			"geocodeDropped", "conflicts", "saveErrors")) {
			progress.put(key, 0);
		}

		String stoppedBy;
		try {
			stoppedBy = geocode(jusoBudget, progress);
		} finally {
			int used = jusoBudget.used() - usedAtStart;
			progress.put(JUSO_CALLS, used);
			progress.apiCalls(used);
		}

		// 좌표가 모자라도 있는 좌표로 연결은 다시 만든다. 예외가 나면 이전 연결이 그대로 남는다(한 트랜잭션).
		RedevelopmentLinkResult links = houseRedevelopmentLogic.rebuildLinks();
		progress.put("linkedRedevelopments", links.redevelopments());
		progress.put("complexes", links.complexes());
		progress.put("linkRows", links.linkRows());

		long pending = houseRedevelopmentLogic.countByGeocodeStatus(GeocodeStatus.PENDING);
		progress.put("geocodePending", pending);
		progress.put("geocodeFailed", houseRedevelopmentLogic.countByGeocodeStatus(GeocodeStatus.FAILED));
		if (pending == 0) {
			return StepOutcome.of(BatchRunStatus.SUCCESS);
		}
		return StepOutcome.of(BatchRunStatus.PARTIAL, stoppedBy != null ? stoppedBy : "SOME_UPSTREAM_FAILED");
	}

	/**
	 * @return 중간에 멈춘 사유. 끝까지 갔으면 null
	 */
	private String geocode(CallBudget budget, StepProgress progress) {
		List<HouseRedevelopment> targets = houseRedevelopmentLogic.findGeocodeTargets();
		progress.put("geocodeTargets", targets.size());
		// 행안부 upstream 실패는 연속 3번이 되기 전까지 저장을 미룬다. 3번 연속이면 우리 쪽 속도 문제로 보고 버린다.
		List<HouseRedevelopment> upstreamFailed = new ArrayList<>();
		String stoppedBy = null;
		for (HouseRedevelopment target : targets) {
			if (budget.remaining() <= 0) {
				stoppedBy = "JUSO_BUDGET";
				break;
			}
			GeocodeResult result = complexGeocoder.search(keywords(target), budget);
			if (result.outcome() == GeocodeResult.Outcome.UPSTREAM_FAILED) {
				upstreamFailed.add(target);
				if (upstreamFailed.size() >= JUSO_UPSTREAM_STREAK_LIMIT) {
					progress.add("geocodeDropped", upstreamFailed.size());
					upstreamFailed.clear();
					stoppedBy = "JUSO_UPSTREAM_STREAK";
					break;
				}
				continue;
			}
			// 연속이 끊겼으니 그전의 개별 upstream 실패는 시도 횟수에 반영한다.
			flushUpstreamFailures(upstreamFailed, progress);
			switch (result.outcome()) {
				case FOUND -> {
					if (apply(target, result, progress)) {
						progress.add("geocodeDone", 1);
					}
				}
				case NOT_FOUND -> {
					if (apply(target, result, progress)) {
						progress.add("geocodeNotFound", 1);
					}
				}
				case NOT_CONFIGURED -> stoppedBy = "JUSO_KEY_MISSING";
				case BUDGET_EXHAUSTED -> stoppedBy = "JUSO_BUDGET";
				case INTERRUPTED -> stoppedBy = "JUSO_INTERRUPTED";
				default -> {
				}
			}
			if (stoppedBy != null) {
				break;
			}
		}
		flushUpstreamFailures(upstreamFailed, progress);
		if (stoppedBy != null) {
			progress.put("geocodeStoppedBy", stoppedBy);
		}
		return stoppedBy;
	}

	/**
	 * 검색어 순서: 관리자가 적은 좌표검색주소, 도로명주소, 지번주소. 값이 없는 검색어는 건너뛴다.
	 * 지번은 "수송동146-12"처럼 붙어 있어도 행안부가 찾는다.
	 */
	static List<String> keywords(HouseRedevelopment redevelopment) {
		LinkedHashSet<String> keywords = new LinkedHashSet<>();
		if (hasText(redevelopment.getGeocodeQuery())) {
			keywords.add(redevelopment.getGeocodeQuery().strip());
		}
		String district = HouseConstant.districtName(redevelopment.getLawdCd());
		String prefix = district == null ? "서울특별시" : "서울특별시 " + district;
		if (hasText(redevelopment.getRoadAddress())) {
			keywords.add(prefix + " " + redevelopment.getRoadAddress().strip());
		}
		if (hasText(redevelopment.getJibunAddress())) {
			keywords.add(prefix + " " + redevelopment.getJibunAddress().strip());
		}
		return List.copyOf(keywords);
	}

	private void flushUpstreamFailures(List<HouseRedevelopment> upstreamFailed, StepProgress progress) {
		for (HouseRedevelopment redevelopment : upstreamFailed) {
			if (apply(redevelopment, GeocodeResult.of(GeocodeResult.Outcome.UPSTREAM_FAILED), progress)) {
				progress.add("geocodeUpstreamFailed", 1);
			}
		}
		upstreamFailed.clear();
	}

	private boolean apply(HouseRedevelopment redevelopment, GeocodeResult result, StepProgress progress) {
		try {
			houseRedevelopmentLogic.applyGeocode(redevelopment, result);
			return true;
		} catch (ObjectOptimisticLockingFailureException e) {
			// 그사이 관리자가 CSV를 다시 올려 이 구역을 바꿨다. 건너뛰고 다음 배치에서 바뀐 값으로 다시 한다.
			progress.add("conflicts", 1);
			return false;
		} catch (RuntimeException e) {
			log.error("정비사업 좌표 저장 실패: redevelopmentId={}, error={}", redevelopment.getId(),
				e.getClass().getSimpleName());
			progress.add("saveErrors", 1);
			return false;
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
