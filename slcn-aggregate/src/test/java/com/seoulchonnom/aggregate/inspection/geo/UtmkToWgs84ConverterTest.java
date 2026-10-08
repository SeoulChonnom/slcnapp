package com.seoulchonnom.aggregate.inspection.geo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UtmkToWgs84ConverterTest {
	/** 1e-5도는 위도 방향 약 1m다. */
	private static final double TOLERANCE_DEGREES = 1e-5;

	private final UtmkToWgs84Converter converter = new UtmkToWgs84Converter();

	@ParameterizedTest(name = "{0}")
	@CsvSource({
		"서울시청, 953898.449, 1952035.979, 37.566535, 126.977969",
		"부산시청, 1143471.285, 1688276.941, 35.179554, 129.075642",
		"성수역, 960774.604, 1949564.899, 37.544581, 127.055961"
	})
	void convert_shouldMatchKnownLocations(String name, double x, double y, double lat, double lon) {
		GeoPoint point = converter.convert(new UtmkPoint(x, y));

		assertThat(point.latitude()).isCloseTo(lat, within(TOLERANCE_DEGREES));
		assertThat(point.longitude()).isCloseTo(lon, within(TOLERANCE_DEGREES));
	}

	@Test
	void convert_shouldBeRepeatableAcrossCalls() {
		UtmkPoint seoul = new UtmkPoint(953898.449, 1952035.979);

		assertThat(converter.convert(seoul)).isEqualTo(converter.convert(seoul));
	}
}
