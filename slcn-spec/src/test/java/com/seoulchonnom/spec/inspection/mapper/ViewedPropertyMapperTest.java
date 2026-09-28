package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole;
import com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType;
import com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemRdo;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerRdo;
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
	void toViewedPropertyDetailRdo_shouldReturnEmptyAnswersForFreshProperty() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);

		var detailRdo = viewedPropertyMapper.toViewedPropertyDetailRdo(property, null, null, Map.of(), null,
			null, null, null, null, null);

		assertThat(detailRdo.getAnswers()).isEmpty();
		assertThat(detailRdo.getTags()).isEmpty();
		assertThat(detailRdo.getPhotos()).isEmpty();
		assertThat(detailRdo.getComplexName()).isEqualTo("트리마제");
	}

	private static PropertyAnswer answer(String questionId, Integer categorySortOrder, int sortOrder) {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setQuestionId(questionId);
		answer.setCategorySortOrder(categorySortOrder);
		answer.setSortOrder(sortOrder);
		return answer;
	}

	/**
	 * 계획 §2: 저장 순서에 기대지 않고 읽을 때 정렬한다. 백필 전후로 배열 순서가 섞여 있어도
	 * 응답은 항상 분류 순서로 나가야 한다.
	 */
	@Test
	void toViewedPropertyDetailRdo_shouldSortAnswersAtReadTimeEvenWhenStoredOrderIsScrambled() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		// 저장 순서는 뒤섞여 있다: 분류 순서(2) -> 분류 순서(1) -> 미분류(null)
		property.setAnswers(List.of(
			answer("q-late", 2, 1),
			answer("q-early", 1, 1),
			answer("q-uncategorized", null, 1)));

		var detailRdo = viewedPropertyMapper.toViewedPropertyDetailRdo(property, null, null, Map.of(), null,
			null, null, null, null, null);

		// 미분류(categorySortOrder=null)는 맨 뒤로 밀린다
		assertThat(detailRdo.getAnswers()).extracting(PropertyAnswerRdo::getQuestionId)
			.containsExactly("q-early", "q-late", "q-uncategorized");
	}

	@Test
	void toViewedPropertyDetailRdo_shouldCarryAreaContextAndNeighbors() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동", 1);
		ViewedPropertyBriefRdo prev = new ViewedPropertyBriefRdo("p0", "트리마제", "101동 1202호", 4);
		ViewedPropertyBriefRdo next = new ViewedPropertyBriefRdo("p2", "트리마제", "101동 1204호", 3);

		var detailRdo = viewedPropertyMapper.toViewedPropertyDetailRdo(property, null, null, Map.of(), null,
			"INSPECTION_AREA-0001", "성수동", "2026-09-17T14:00:00", prev, next);

		assertThat(detailRdo.getAreaId()).isEqualTo("INSPECTION_AREA-0001");
		assertThat(detailRdo.getAreaName()).isEqualTo("성수동");
		assertThat(detailRdo.getVisitedAt()).isEqualTo("2026-09-17T14:00:00");
		assertThat(detailRdo.getPrevProperty()).isSameAs(prev);
		assertThat(detailRdo.getNextProperty()).isSameAs(next);
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
