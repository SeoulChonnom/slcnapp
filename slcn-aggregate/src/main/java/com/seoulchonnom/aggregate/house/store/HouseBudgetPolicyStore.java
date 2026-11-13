package com.seoulchonnom.aggregate.house.store;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.exception.HouseBudgetPolicyNotFoundException;
import com.seoulchonnom.aggregate.house.store.mapper.HouseBudgetPolicyJpoMapper;
import com.seoulchonnom.aggregate.house.store.repository.HouseBudgetPolicyRepository;
import com.seoulchonnom.spec.house.entity.HouseBudgetPolicy;
import com.seoulchonnom.spec.house.entity.vo.BrokerageTier;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class HouseBudgetPolicyStore {
	private final HouseBudgetPolicyRepository houseBudgetPolicyRepository;
	private final HouseBudgetPolicyJpoMapper houseBudgetPolicyJpoMapper;

	/**
	 * 오늘 시행 중인 정책 하나. 없으면 시드 누락이다.
	 *
	 * 정책 행은 수동 SQL로만 들어가므로 읽을 때 정렬 규칙을 검사한다. 규칙이 깨진 행으로
	 * FE가 계산하면 사용자에게 틀린 예산이 나가므로, 서버 로그에 원인을 남기고 503으로 낸다.
	 */
	public HouseBudgetPolicy findEffective(LocalDate today) {
		HouseBudgetPolicy policy = houseBudgetPolicyRepository
			.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today)
			.map(houseBudgetPolicyJpoMapper::toDomain)
			.orElseThrow(() -> new HouseBudgetPolicyNotFoundException());
		String problem = findConsistencyProblem(policy);
		if (problem != null) {
			log.error("예산 정책 행이 규칙에 맞지 않습니다. policyId={}, effectiveFrom={}, 사유={}",
				policy.getId(), policy.getEffectiveFrom(), problem);
			throw new HouseBudgetPolicyNotFoundException();
		}
		return policy;
	}

	/**
	 * @return 문제가 없으면 null, 있으면 사유
	 */
	static String findConsistencyProblem(HouseBudgetPolicy policy) {
		if (policy.getLtvFirstTime() == null || policy.getLtvNonFirst() == null) {
			return "ltv 값이 비어 있음";
		}
		String capProblem = priceCapProblem(policy.getPriceCaps());
		if (capProblem != null) {
			return "priceCaps: " + capProblem;
		}
		CostRates costRates = policy.getCostRates();
		if (costRates == null || costRates.getAcquisitionTax() == null || costRates.getFirstTimeRelief() == null
			|| costRates.getBrokerage() == null || costRates.getRegistrationRatePct() == null
			|| costRates.getBrokerage().getVatRatio() == null) {
			return "costRates 필수 항목이 비어 있음";
		}
		String tierProblem = brokerageTierProblem(costRates.getBrokerage().getTiers());
		if (tierProblem != null) {
			return "costRates.brokerage.tiers: " + tierProblem;
		}
		return null;
	}

	private static String priceCapProblem(List<PriceCap> caps) {
		if (caps == null || caps.isEmpty()) {
			return "비어 있음";
		}
		Long previous = null;
		for (int i = 0; i < caps.size(); i++) {
			PriceCap cap = caps.get(i);
			if (cap == null) {
				return i + "번째 항목이 null";
			}
			boolean last = i == caps.size() - 1;
			Long max = cap.getMaxPriceManwon();
			if (last != (max == null)) {
				return "마지막 항목만 maxPriceManwon이 null이어야 함";
			}
			if (max != null && previous != null && max <= previous) {
				return "maxPriceManwon이 오름차순이 아님";
			}
			previous = max;
		}
		return null;
	}

	private static String brokerageTierProblem(List<BrokerageTier> tiers) {
		if (tiers == null || tiers.isEmpty()) {
			return "비어 있음";
		}
		Long previous = null;
		for (int i = 0; i < tiers.size(); i++) {
			BrokerageTier tier = tiers.get(i);
			if (tier == null || tier.getRatePct() == null) {
				return i + "번째 항목이 비어 있음";
			}
			boolean last = i == tiers.size() - 1;
			Long below = tier.getBelowManwon();
			if (last != (below == null)) {
				return "마지막 항목만 belowManwon이 null이어야 함";
			}
			if (below != null && previous != null && below <= previous) {
				return "belowManwon이 오름차순이 아님";
			}
			previous = below;
		}
		return null;
	}
}
