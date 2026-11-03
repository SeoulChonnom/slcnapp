package com.seoulchonnom.aggregate.house.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.house.collect.TradeRecord;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;
import com.seoulchonnom.spec.house.entity.vo.RegisterStatus;

import lombok.RequiredArgsConstructor;

/**
 * 단지 생성과 표시 정보 갱신. 좌표·건축물대장 보강 결과 반영은 보강 단계 몫이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseComplexLogic {
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
