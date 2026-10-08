package com.seoulchonnom.aggregate.inspection.store.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyJpo;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyLocation;

class ViewedPropertyJpoMapperTest {
	private final ViewedPropertyJpoMapper mapper = new ViewedPropertyJpoMapper();

	@Test
	void roundTrip_shouldKeepLocation() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		PropertyLocation location = new PropertyLocation("1171010200", "서울 송파구", 37.5, 127.1, 960000.5, 1950000.5);
		property.changeLocation(location);

		ViewedPropertyJpo jpo = mapper.toJpo(property);

		assertThat(jpo.getBdMgtSn()).isEqualTo("1171010200");
		assertThat(jpo.getRoadAddress()).isEqualTo("서울 송파구");
		assertThat(jpo.getLatitude()).isEqualTo(37.5);
		assertThat(jpo.getLongitude()).isEqualTo(127.1);
		assertThat(jpo.getEntX()).isEqualTo(960000.5);
		assertThat(jpo.getEntY()).isEqualTo(1950000.5);
		assertThat(mapper.toDomain(jpo).getLocation()).isEqualTo(location);
	}

	@Test
	void roundTrip_shouldMapNullLocationToNullColumnsAndBack() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);

		ViewedPropertyJpo jpo = mapper.toJpo(property);

		assertThat(jpo.getBdMgtSn()).isNull();
		assertThat(jpo.getRoadAddress()).isNull();
		assertThat(jpo.getLatitude()).isNull();
		assertThat(jpo.getLongitude()).isNull();
		assertThat(jpo.getEntX()).isNull();
		assertThat(jpo.getEntY()).isNull();
		assertThat(mapper.toDomain(jpo).getLocation()).isNull();
	}
}
