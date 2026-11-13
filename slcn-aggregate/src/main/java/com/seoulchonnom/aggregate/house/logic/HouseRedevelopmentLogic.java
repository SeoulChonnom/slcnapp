package com.seoulchonnom.aggregate.house.logic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.house.collect.GeocodeResult;
import com.seoulchonnom.aggregate.house.redevelopment.RedevelopmentRow;
import com.seoulchonnom.aggregate.house.stat.RedevelopmentLinkCalculator;
import com.seoulchonnom.aggregate.house.stat.RedevelopmentLinkCalculator.Link;
import com.seoulchonnom.aggregate.house.store.HouseComplexStore;
import com.seoulchonnom.aggregate.house.store.HouseRedevelopmentStore;
import com.seoulchonnom.aggregate.house.store.projection.ComplexPointPdo;
import com.seoulchonnom.spec.house.entity.HouseRedevelopment;
import com.seoulchonnom.spec.house.entity.vo.GeocodeStatus;

import lombok.RequiredArgsConstructor;

/**
 * 정비사업 구역의 통째 교체, 좌표 결과 반영, 단지 연결 재생성. 외부 호출은 트랜잭션 밖(5단계 Step)에서 끝내고
 * 이 클래스는 받은 값만 저장한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HouseRedevelopmentLogic {
	/** 좌표 개별 upstream 실패가 이만큼 쌓이면 FAILED다(단지와 같은 규칙). */
	static final int MAX_ATTEMPTS = 3;

	private final HouseRedevelopmentStore houseRedevelopmentStore;
	private final HouseComplexStore houseComplexStore;

	/**
	 * 파일의 구역으로 테이블을 통째로 바꾼다. source_key(=CODE)가 같으면 갱신, 없으면 추가, 파일에 없는 구역은 그 구역의
	 * 단지 연결과 함께 지운다. 한 트랜잭션이라 중간에 실패하면 전부 되돌아간다.
	 * 주소 관련 값(지번·도로명·좌표검색주소·자치구)이 바뀐 구역과 새 구역은 좌표를 PENDING으로 되돌린다.
	 * 바뀐 게 없으면 UPDATE하지 않는다(좌표·확인일도 그대로).
	 * 빈 목록은 거절한다. 잘못된 파일로 전부 지우는 일을 막으려는 것이다.
	 */
	@Transactional
	public RedevelopmentReplaceResult replaceAll(List<RedevelopmentRow> rows, LocalDate today) {
		if (rows == null || rows.isEmpty()) {
			throw new IllegalArgumentException("정비사업 목록이 비어 있습니다.");
		}
		Map<String, HouseRedevelopment> existing = new HashMap<>();
		for (HouseRedevelopment redevelopment : houseRedevelopmentStore.findAll()) {
			existing.put(redevelopment.getSourceKey(), redevelopment);
		}
		int inserted = 0;
		int updated = 0;
		int unchanged = 0;
		long now = System.currentTimeMillis();
		for (RedevelopmentRow row : rows) {
			HouseRedevelopment current = existing.remove(row.code());
			if (current == null) {
				houseRedevelopmentStore.save(newRedevelopment(row, today));
				inserted++;
				continue;
			}
			if (!isChanged(current, row)) {
				unchanged++;
				continue;
			}
			boolean addressChanged = isAddressChanged(current, row);
			current.setName(row.name());
			current.setLawdCd(row.lawdCd());
			current.setJibunAddress(row.jibunAddress());
			current.setRoadAddress(row.roadAddress());
			current.setGeocodeQuery(row.geocodeQuery());
			current.setProjectType(row.projectType());
			current.setStage(row.stage());
			current.setSyncedDate(today);
			current.setModifiedTime(now);
			if (addressChanged) {
				resetGeocode(current);
			}
			houseRedevelopmentStore.save(current);
			updated++;
		}
		// 파일에 없는 구역이 existing에 남는다.
		houseRedevelopmentStore.deleteWithLinks(existing.values().stream().map(HouseRedevelopment::getId).toList());
		long pending = houseRedevelopmentStore.countByGeocodeStatus(GeocodeStatus.PENDING);
		return new RedevelopmentReplaceResult(rows.size(), inserted, updated, unchanged, existing.size(), pending);
	}

	private static HouseRedevelopment newRedevelopment(RedevelopmentRow row, LocalDate today) {
		return HouseRedevelopment.builder().sourceKey(row.code()).name(row.name()).lawdCd(row.lawdCd())
			.jibunAddress(row.jibunAddress()).roadAddress(row.roadAddress()).geocodeQuery(row.geocodeQuery())
			.projectType(row.projectType()).stage(row.stage()).geocodeStatus(GeocodeStatus.PENDING)
			.geocodeAttempts(0).syncedDate(today).build();
	}

	private static boolean isChanged(HouseRedevelopment current, RedevelopmentRow row) {
		return isAddressChanged(current, row) || !Objects.equals(current.getName(), row.name())
			|| !Objects.equals(current.getProjectType(), row.projectType())
			|| !Objects.equals(current.getStage(), row.stage());
	}

	private static boolean isAddressChanged(HouseRedevelopment current, RedevelopmentRow row) {
		return !Objects.equals(current.getLawdCd(), row.lawdCd())
			|| !Objects.equals(current.getJibunAddress(), row.jibunAddress())
			|| !Objects.equals(current.getRoadAddress(), row.roadAddress())
			|| !Objects.equals(current.getGeocodeQuery(), row.geocodeQuery());
	}

	private static void resetGeocode(HouseRedevelopment redevelopment) {
		redevelopment.setGeocodeStatus(GeocodeStatus.PENDING);
		redevelopment.setGeocodeAttempts(0);
		redevelopment.setLatitude(null);
		redevelopment.setLongitude(null);
	}

	public long count() {
		return houseRedevelopmentStore.count();
	}

	public long countByGeocodeStatus(GeocodeStatus status) {
		return houseRedevelopmentStore.countByGeocodeStatus(status);
	}

	/**
	 * 좌표를 기다리는 구역 전부. 수백 건이라 한 번에 읽는다.
	 */
	public List<HouseRedevelopment> findGeocodeTargets() {
		return houseRedevelopmentStore.findPending();
	}

	/**
	 * 좌표 결과를 반영한다. 구역 1건 = 트랜잭션 1개이고, 그사이 CSV 교체가 같은 구역을 바꿨으면
	 * ObjectOptimisticLockingFailureException이 난다(호출한 쪽이 건너뛴다).
	 * FOUND는 DONE, NOT_FOUND는 즉시 FAILED, UPSTREAM_FAILED는 시도 횟수 +1(3이면 FAILED, 아니면 PENDING 유지).
	 */
	@Transactional
	public HouseRedevelopment applyGeocode(HouseRedevelopment redevelopment, GeocodeResult result) {
		switch (result.outcome()) {
			case FOUND -> {
				redevelopment.setLatitude(result.latitude());
				redevelopment.setLongitude(result.longitude());
				redevelopment.setGeocodeStatus(GeocodeStatus.DONE);
				redevelopment.setGeocodeAttempts(0);
			}
			case NOT_FOUND -> redevelopment.setGeocodeStatus(GeocodeStatus.FAILED);
			case UPSTREAM_FAILED -> {
				redevelopment.setGeocodeAttempts(redevelopment.getGeocodeAttempts() + 1);
				redevelopment.setGeocodeStatus(redevelopment.getGeocodeAttempts() >= MAX_ATTEMPTS
					? GeocodeStatus.FAILED : GeocodeStatus.PENDING);
			}
			default -> throw new IllegalArgumentException("반영할 수 없는 좌표 결과: " + result.outcome());
		}
		redevelopment.setModifiedTime(System.currentTimeMillis());
		return houseRedevelopmentStore.save(redevelopment);
	}

	/**
	 * 좌표 있는 단지 x 좌표 DONE 구역의 반경 안 연결을 통째로 다시 만든다. 한 트랜잭션이라 실패하면 이전 연결이 남는다.
	 */
	@Transactional
	public RedevelopmentLinkResult rebuildLinks() {
		List<ComplexPointPdo> complexes = houseComplexStore.findGeocodedPoints();
		List<HouseRedevelopment> redevelopments = houseRedevelopmentStore.findGeocoded();
		List<Link> links = new ArrayList<>(RedevelopmentLinkCalculator.calculate(complexes, redevelopments));
		int rows = houseRedevelopmentStore.replaceLinks(links);
		return new RedevelopmentLinkResult(redevelopments.size(), complexes.size(), rows);
	}

	public List<HouseRedevelopment> findIncomplete(int page, int size) {
		return houseRedevelopmentStore.findIncomplete(page, size);
	}

	public long countIncomplete() {
		return houseRedevelopmentStore.countIncomplete();
	}
}
