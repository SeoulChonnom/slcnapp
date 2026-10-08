package com.seoulchonnom.aggregate.flow.inspection;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.inspection.geo.GeoPoint;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteGateway;
import com.seoulchonnom.aggregate.inspection.geo.WalkingRouteSegment;
import com.seoulchonnom.aggregate.inspection.store.InspectionVisitStore;
import com.seoulchonnom.aggregate.inspection.store.ViewedPropertyStore;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;
import com.seoulchonnom.spec.inspection.facade.sdo.WalkingRouteLegRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.WalkingRouteRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.WalkingRouteStopRdo;

import lombok.RequiredArgsConstructor;

/**
 * 임장 상세와 같은 매물 순서로 도보 경로를 만든다. 아무것도 저장하거나 캐시하지 않는다.
 * 외부 호출(최대 수 회)이 이어지는 동안 트랜잭션을 잡지 않으려고 @Transactional을 붙이지 않는다.
 *
 * 외부로는 좌표만 나간다. 임장 ID와 매물 ID, 주소는 게이트웨이에 넘기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WalkingRouteQueryFlow {
	/** 카카오 한 번의 호출에 담을 수 있는 최대 지점 수(출발 + 경유 5 + 도착). */
	static final int MAX_POINTS_PER_CALL = 7;

	private final InspectionVisitStore inspectionVisitStore;
	private final ViewedPropertyStore viewedPropertyStore;
	private final WalkingRouteGateway walkingRouteGateway;

	public WalkingRouteRdo getWalkingRoute(String visitId) {
		inspectionVisitStore.findById(visitId);
		// 상세 조회(InspectionVisitQueryFlow.getInspectionVisit)와 같은 메서드를 써서 순서가 어긋나지 않게 한다.
		List<ViewedProperty> properties = viewedPropertyStore.findAllByVisitId(visitId);

		List<Stop> stops = toStops(properties);
		if (stops.size() < 2) {
			return new WalkingRouteRdo(0, 0, List.of(), List.of());
		}

		int totalDistance = 0;
		int totalTime = 0;
		List<WalkingRouteLegRdo> legs = new ArrayList<>();
		// 앞 묶음의 끝 지점이 다음 묶음의 시작 지점이 되도록 겹쳐 나눈다. 호출 수는 ceil((k-1)/6)이다.
		int step = MAX_POINTS_PER_CALL - 1;
		for (int from = 0; from < stops.size() - 1; from += step) {
			int to = Math.min(from + step, stops.size() - 1);
			List<GeoPoint> points = stops.subList(from, to + 1).stream().map(Stop::point).toList();
			WalkingRouteSegment segment = walkingRouteGateway.route(points);
			totalDistance += segment.totalDistance();
			totalTime += segment.totalTime();
			for (int i = 0; i < segment.legs().size(); i++) {
				WalkingRouteSegment.Leg leg = segment.legs().get(i);
				legs.add(new WalkingRouteLegRdo(from + i, from + i + 1, leg.distance(), leg.time(),
					leg.path().stream().map(p -> List.of(p.longitude(), p.latitude())).toList()));
			}
		}

		List<WalkingRouteStopRdo> stopRdos = new ArrayList<>();
		for (int i = 0; i < stops.size(); i++) {
			Stop stop = stops.get(i);
			stopRdos.add(new WalkingRouteStopRdo(i, List.copyOf(stop.propertyIds()), stop.point().latitude(),
				stop.point().longitude()));
		}
		return new WalkingRouteRdo(totalDistance, totalTime, stopRdos, legs);
	}

	/**
	 * 위치 없는 매물은 건너뛰고, 바로 이어지는 같은 좌표는 한 지점으로 합친다.
	 * 떨어져서 다시 나오는 좌표(A, B, A)는 합치지 않는다. 화면 순서를 그대로 따르기 위해서다.
	 * 위치 없는 매물을 사이에 두고 이어진 같은 좌표(A, 위치 없음, A)는 건너뛴 뒤 연속이므로 합쳐진다.
	 */
	private List<Stop> toStops(List<ViewedProperty> properties) {
		List<Stop> stops = new ArrayList<>();
		for (ViewedProperty property : properties) {
			PropertyLocation location = property.getLocation();
			if (location == null) {
				continue;
			}
			GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
			Stop last = stops.isEmpty() ? null : stops.get(stops.size() - 1);
			if (last != null && last.point().equals(point)) {
				last.propertyIds().add(property.getId());
			} else {
				Stop stop = new Stop(point, new ArrayList<>());
				stop.propertyIds().add(property.getId());
				stops.add(stop);
			}
		}
		return stops;
	}

	private record Stop(GeoPoint point, List<String> propertyIds) {
	}
}
