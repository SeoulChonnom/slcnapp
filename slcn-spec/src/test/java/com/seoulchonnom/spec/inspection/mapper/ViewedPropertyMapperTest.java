package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyBriefRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyRdo;

class ViewedPropertyMapperTest {
	private final ViewedPropertyMapper viewedPropertyMapper = new ViewedPropertyMapper(new PropertyAnswerMapper());

	private static FileBoxItemRdo item(String id, FileBoxTargetType targetType, String targetId,
		FileBoxItemRole role) {
		FileBoxItemRdo rdo = new FileBoxItemRdo();
		rdo.setId(id);
		rdo.setTargetType(targetType);
		rdo.setTargetId(targetId);
		rdo.setRole(role);
		return rdo;
	}

	@Test
	void toViewedPropertyRdo_shouldPickOnlyItsOwnPhotos() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
		List<FileBoxItemRdo> files = List.of(
			item("visit-cover", FileBoxTargetType.INSPECTION_VISIT, null, FileBoxItemRole.COVER),
			item("mine-cover", FileBoxTargetType.VIEWED_PROPERTY, property.getId(), FileBoxItemRole.COVER),
			item("mine-gallery", FileBoxTargetType.VIEWED_PROPERTY, property.getId(), FileBoxItemRole.GALLERY),
			item("other-gallery", FileBoxTargetType.VIEWED_PROPERTY, "other-property", FileBoxItemRole.GALLERY));

		ViewedPropertyRdo rdo = viewedPropertyMapper.toViewedPropertyRdo(property, List.of("남향"), files);

		assertThat(rdo.getCover().getId()).isEqualTo("mine-cover");
		assertThat(rdo.getPhotos()).extracting(FileBoxItemRdo::getId).containsExactly("mine-gallery");
		assertThat(rdo.getTags()).containsExactly("남향");
	}

	@Test
	void toViewedPropertyDetailRdo_shouldKeepAnswerOrderAsGiven() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);

		var detailRdo = viewedPropertyMapper.toViewedPropertyDetailRdo(property, null, null, Map.of(), null);

		assertThat(detailRdo.getAnswers()).isEmpty();
		assertThat(detailRdo.getTags()).isEmpty();
		assertThat(detailRdo.getPhotos()).isEmpty();
		assertThat(detailRdo.getComplexName()).isEqualTo("트리마제");
	}

	@Test
	void toViewedPropertyBriefRdo_shouldReturnNullForMissingProperty() {
		assertThat(viewedPropertyMapper.toViewedPropertyBriefRdo(null)).isNull();
	}

	@Test
	void toViewedPropertyBriefRdo_shouldCarryComplexName() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
		property.setInterestLevel(5);

		ViewedPropertyBriefRdo rdo = viewedPropertyMapper.toViewedPropertyBriefRdo(property);

		assertThat(rdo.getComplexName()).isEqualTo("트리마제");
		assertThat(rdo.getName()).isEqualTo("101동 1203호");
		assertThat(rdo.getInterestLevel()).isEqualTo(5);
	}
}
