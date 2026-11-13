package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.collect.RegisterLookupResult;
import com.seoulchonnom.aggregate.house.collect.RegisterSummary;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.exception.HouseComplexNotFoundException;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

import lombok.RequiredArgsConstructor;

/**
 * 단지 생성과 표시 정보 갱신, 좌표·건축물대장 보강 결과 반영, 관리자 주소 보정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseComplexLogic {
	/** 호출 실패가 이만큼 쌓이면 보강을 포기하고 FAILED로 둔다. */
	static final int MAX_ATTEMPTS = 3;

	private final HouseComplexStore houseComplexStore;
	private final IdGenerator idGenerator;

	/**
	 * 단위에 나온 aptSeq마다 단지를 확정한다. 없으면 새로 만들고(좌표·대장 PENDING),
	 * 있으면 그 단위에서 가장 최근 계약인 레코드로 표시 정보를 갱신한다. 값이 같으면 저장하지 않아 UPDATE가 나가지 않는다.
	 *
	 * @param latestRecordByAptSeq aptSeq별로 단위 안에서 계약일이 가장 늦은 레코드
	 */
	@Transactional
	public ComplexResolution resolve(String lawdCd, Map<String, TradeRecord> latestRecordByAptSeq) {
		Map<String, HouseComplex> existing = houseComplexStore.findByAptSeqIn(latestRecordByAptSeq.keySet());
		Map<String, String> idByAptSeq = new HashMap<>();
		int created = 0;
		int refreshed = 0;
		// ID 채번 순서가 실행마다 같도록 aptSeq 순으로 돈다.
		for (Map.Entry<String, TradeRecord> entry : new TreeMap<>(latestRecordByAptSeq).entrySet()) {
			String aptSeq = entry.getKey();
			TradeRecord record = entry.getValue();
			HouseComplex complex = existing.get(aptSeq);
			if (complex == null) {
				complex = houseComplexStore.save(newComplex(aptSeq, lawdCd, record));
				created++;
			} else if (refreshDisplay(complex, record)) {
				houseComplexStore.save(complex);
				refreshed++;
			}
			idByAptSeq.put(aptSeq, complex.getId());
		}
		return new ComplexResolution(idByAptSeq, created, refreshed);
	}

	/**
	 * 오늘 좌표를 구할 단지. 최근 12개월 거래가 많은 단지부터 최대 limit개.
	 */
	public List<HouseComplex> findGeocodeTargets(LocalDate today, int limit) {
		return houseComplexStore.findGeocodeTargets(today, limit);
	}

	/**
	 * 오늘 건축물대장을 조회할 단지. 처음 조회하는 단지(거래 많은 순)가 먼저, 남는 자리에 30일 이상 지난 갱신 대상이 온다.
	 */
	public List<HouseComplex> findRegisterTargets(LocalDate today, int limit) {
		return houseComplexStore.findRegisterTargets(today, limit);
	}

	/**
	 * 관리자 목록 한 페이지(최근 12개월 거래 건수 내림차순)와 전체 건수.
	 */
	public List<IncompleteComplexPdo> findIncomplete(LocalDate today, int limit, int offset) {
		return houseComplexStore.findIncomplete(today, limit, offset);
	}

	public long countIncomplete() {
		return houseComplexStore.countIncomplete();
	}

	public long countTrades12m(String complexId, LocalDate today) {
		return houseComplexStore.countTrades12m(complexId, today);
	}

	/**
	 * 좌표 결과를 반영한다. 단지 1건 = 트랜잭션 1개. 넘겨받은 단지의 @Version으로 저장하므로, 보강하는 사이
	 * 관리자가 같은 단지를 보정했으면 ObjectOptimisticLockingFailureException이 난다. 호출한 쪽이 그 단지를 건너뛴다.
	 *
	 * FOUND는 DONE, NOT_FOUND는 즉시 FAILED, UPSTREAM_FAILED는 시도 횟수 +1(3이 되면 FAILED, 아니면 PENDING 유지).
	 * 그 밖의 결과(예산 소진 등)는 반영할 것이 없어 호출하지 않는다.
	 */
	@Transactional
	public HouseComplex applyGeocode(HouseComplex complex, GeocodeResult result) {
		switch (result.outcome()) {
			case FOUND -> {
				complex.setLatitude(result.latitude());
				complex.setLongitude(result.longitude());
				complex.setRoadAddress(result.roadAddress());
				complex.setGeocodeStatus(GeocodeStatus.DONE);
				complex.setGeocodeAttempts(0);
			}
			case NOT_FOUND -> complex.setGeocodeStatus(GeocodeStatus.FAILED);
			case UPSTREAM_FAILED -> {
				complex.setGeocodeAttempts(complex.getGeocodeAttempts() + 1);
				complex.setGeocodeStatus(complex.getGeocodeAttempts() >= MAX_ATTEMPTS ? GeocodeStatus.FAILED
					: GeocodeStatus.PENDING);
			}
			default -> throw new IllegalArgumentException("반영할 수 없는 좌표 결과: " + result.outcome());
		}
		return houseComplexStore.save(complex);
	}

	/**
	 * 건축물대장 결과를 반영한다. 단지 1건 = 트랜잭션 1개이고 @Version 충돌은 applyGeocode와 같다.
	 *
	 * 처음 조회하는 단지(PENDING): FOUND는 DONE, NOT_FOUND는 즉시 FAILED, FAILED(호출 실패)는 시도 횟수 +1(3이면 FAILED).
	 * 이미 DONE인 단지의 갱신 조회: FOUND는 값을 새로 덮고, NOT_FOUND는 기존 값을 두고 다음 갱신을 30일 뒤로 미루며,
	 * 호출 실패는 아무것도 바꾸지 않아 다음 날 다시 한다. 좋은 값이 일시 장애나 대장 변경으로 지워지지 않게 하려는 것이다.
	 */
	@Transactional
	public HouseComplex applyRegister(HouseComplex complex, RegisterLookupResult result, LocalDate today) {
		boolean renewal = complex.getRegisterStatus() == RegisterStatus.DONE;
		switch (result.outcome()) {
			case FOUND -> {
				RegisterSummary summary = result.summary();
				complex.setHouseholds(summary.households());
				complex.setFarPct(summary.farPct());
				complex.setBcrPct(summary.bcrPct());
				complex.setApprovalDate(summary.approvalDate());
				complex.setFarSource(summary.farSource());
				complex.setRegisterStatus(RegisterStatus.DONE);
				complex.setRegisterAttempts(0);
				complex.setRegisterSyncedDate(today);
			}
			case NOT_FOUND -> {
				if (renewal) {
					complex.setRegisterSyncedDate(today);
				} else {
					complex.setRegisterStatus(RegisterStatus.FAILED);
				}
			}
			case FAILED -> {
				if (renewal) {
					return complex;
				}
				complex.setRegisterAttempts(complex.getRegisterAttempts() + 1);
				complex.setRegisterStatus(complex.getRegisterAttempts() >= MAX_ATTEMPTS ? RegisterStatus.FAILED
					: RegisterStatus.PENDING);
			}
			default -> throw new IllegalArgumentException("반영할 수 없는 건축물대장 결과: " + result.outcome());
		}
		return houseComplexStore.save(complex);
	}

	/**
	 * 관리자 주소 보정. addressOverride를 통째로 교체하고, 바뀐 입력에 맞춰 보강 상태를 PENDING으로 되돌린다.
	 * 실제 재조회는 다음 야간 배치다.
	 * - roadAddress가 있으면 좌표를 다시 한다.
	 * - bonbun이 있으면 건축물대장을 다시 하고, 지번은 좌표 검색어 3순위에도 쓰이므로 좌표도 다시 한다.
	 *
	 * @param override 정규화된 보정값(본번·부번은 0 채움 4자리). 비어 있지 않아야 한다.
	 */
	@Transactional
	public HouseComplex overrideAddress(String complexId, AddressOverride override) {
		HouseComplex complex = houseComplexStore.findById(complexId).orElseThrow(HouseComplexNotFoundException::new);
		complex.setAddressOverride(override);
		if (override.getRoadAddress() != null || override.getBonbun() != null) {
			complex.setGeocodeStatus(GeocodeStatus.PENDING);
			complex.setGeocodeAttempts(0);
		}
		if (override.getBonbun() != null) {
			complex.setRegisterStatus(RegisterStatus.PENDING);
			complex.setRegisterAttempts(0);
		}
		return houseComplexStore.save(complex);
	}

	private HouseComplex newComplex(String aptSeq, String lawdCd, TradeRecord record) {
		HouseComplex complex = HouseComplex.builder()
			.aptSeq(aptSeq)
			.lawdCd(lawdCd)
			.umdCd(record.umdCd())
			.umdName(record.umdName())
			.bonbun(record.bonbun())
			.bubun(record.bubun())
			.aptName(record.aptName())
			.roadName(record.roadName())
			.roadBonbun(record.roadBonbun())
			.roadBubun(record.roadBubun())
			.buildYear(record.buildYear())
			.geocodeStatus(GeocodeStatus.PENDING)
			.registerStatus(RegisterStatus.PENDING)
			.build();
		complex.setId(idGenerator.nextDomainId(SequenceName.HOUSE_COMPLEX.toString()));
		return complex;
	}

	/**
	 * 레코드에 값이 있고 다를 때만 덮는다. 빈 값으로 기존 값을 지우지 않는다.
	 *
	 * @return 하나라도 바뀌었는지
	 */
	private boolean refreshDisplay(HouseComplex complex, TradeRecord record) {
		boolean changed = false;
		if (differs(complex.getAptName(), record.aptName())) {
			complex.setAptName(record.aptName());
			changed = true;
		}
		if (differs(complex.getUmdCd(), record.umdCd())) {
			complex.setUmdCd(record.umdCd());
			changed = true;
		}
		if (differs(complex.getUmdName(), record.umdName())) {
			complex.setUmdName(record.umdName());
			changed = true;
		}
		if (differs(complex.getBonbun(), record.bonbun())) {
			complex.setBonbun(record.bonbun());
			changed = true;
		}
		if (differs(complex.getBubun(), record.bubun())) {
			complex.setBubun(record.bubun());
			changed = true;
		}
		if (differs(complex.getRoadName(), record.roadName())) {
			complex.setRoadName(record.roadName());
			changed = true;
		}
		if (differs(complex.getRoadBonbun(), record.roadBonbun())) {
			complex.setRoadBonbun(record.roadBonbun());
			changed = true;
		}
		if (differs(complex.getRoadBubun(), record.roadBubun())) {
			complex.setRoadBubun(record.roadBubun());
			changed = true;
		}
		if (differs(complex.getBuildYear(), record.buildYear())) {
			complex.setBuildYear(record.buildYear());
			changed = true;
		}
		return changed;
	}

	private static boolean differs(Object current, Object incoming) {
		return incoming != null && !Objects.equals(current, incoming);
	}
}
