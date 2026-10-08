package com.seoulchonnom.aggregate.inspection.geo;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

/**
 * 행안부 entX/entY(EPSG:5179, GRS80 UTM-K)를 WGS84 위경도로 바꾼다.
 *
 * 변환 객체는 한 번만 만들어 재사용한다. proj4j의 CoordinateTransform은 내부 상태를 바꾸지 않지만
 * 공식적으로 스레드 안전이 보장되지는 않으므로, 입출력 좌표(ProjCoordinate)는 호출마다 새로 만들고
 * transform 호출만 synchronized로 직렬화한다. 호출 빈도가 낮아(2인 서비스, 매물 저장 시 1회) 비용이 없다.
 *
 * GRS80과 WGS84는 타원체 차이가 mm 단위라 데이텀 이동 없이 변환한다(+towgs84 생략).
 */
@Component
public class UtmkToWgs84Converter {
	private static final String UTMK_PROJ4 = "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 "
		+ "+ellps=GRS80 +units=m +no_defs";
	private static final String WGS84_PROJ4 = "+proj=longlat +datum=WGS84 +no_defs";

	private final CoordinateTransform transform;

	public UtmkToWgs84Converter() {
		CRSFactory crsFactory = new CRSFactory();
		CoordinateReferenceSystem utmk = crsFactory.createFromParameters("EPSG:5179", UTMK_PROJ4);
		CoordinateReferenceSystem wgs84 = crsFactory.createFromParameters("EPSG:4326", WGS84_PROJ4);
		this.transform = new CoordinateTransformFactory().createTransform(utmk, wgs84);
	}

	public GeoPoint convert(UtmkPoint point) {
		ProjCoordinate source = new ProjCoordinate(point.x(), point.y());
		ProjCoordinate target = new ProjCoordinate();
		synchronized (transform) {
			transform.transform(source, target);
		}
		return new GeoPoint(target.y, target.x);
	}
}
