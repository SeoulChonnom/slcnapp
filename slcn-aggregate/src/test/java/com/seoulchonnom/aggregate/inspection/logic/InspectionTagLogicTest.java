package com.seoulchonnom.aggregate.inspection.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.inspection.store.InspectionTagStore;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionTagScope;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionTagRdo;
import com.seoulchonnom.spec.inspection.mapper.InspectionTagMapper;

class InspectionTagLogicTest {
	private final InspectionTagStore inspectionTagStore = mock(InspectionTagStore.class);
	private final InspectionTagLogic inspectionTagLogic = new InspectionTagLogic(inspectionTagStore,
		new InspectionTagMapper());

	private static InspectionTag tag(String id, String name) {
		InspectionTag tag = InspectionTag.builder().name(name).build();
		tag.setId(id);
		return tag;
	}

	@SuppressWarnings("unchecked")
	private List<String> capturedNames() {
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(inspectionTagStore).getOrCreateAll(captor.capture());
		return captor.getValue();
	}

	@Test
	void resolveTags_shouldNormalizeAndDeduplicate() {
		when(inspectionTagStore.getOrCreateAll(anyList())).thenReturn(List.of());

		inspectionTagLogic.resolveTags(List.of("#한강", " 한강 ", "직주  근접", "  "));

		assertThat(capturedNames()).containsExactly("한강", "직주 근접");
	}

	@Test
	void resolveTags_shouldRejectMoreThanTenTags() {
		List<String> names = List.of("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10", "t11");

		assertThatThrownBy(() -> inspectionTagLogic.resolveTags(names))
			.isInstanceOf(BadRequestException.class);
		verify(inspectionTagStore, never()).getOrCreateAll(anyList());
	}

	/**
	 * inspection_tag.name이 varchar(50)이다. 여기서 막지 않으면 flush 시점에
	 * DataIntegrityViolationException이 나고 전역 핸들러가 잡지 못해 500이 된다.
	 */
	@Test
	void resolveTags_shouldRejectTagNameLongerThanColumn() {
		assertThatThrownBy(() -> inspectionTagLogic.resolveTags(List.of("가".repeat(51))))
			.isInstanceOf(BadRequestException.class);
		verify(inspectionTagStore, never()).getOrCreateAll(anyList());
	}

	@Test
	void resolveTags_shouldAcceptTagNameAtColumnLimit() {
		when(inspectionTagStore.getOrCreateAll(anyList())).thenReturn(List.of());

		inspectionTagLogic.resolveTags(List.of("가".repeat(50)));

		verify(inspectionTagStore).getOrCreateAll(anyList());
	}

	@Test
	void resolveTags_shouldTreatNullAsNoChange() {
		assertThat(inspectionTagLogic.resolveTags(null)).isEmpty();
		verify(inspectionTagStore, never()).getOrCreateAll(anyList());
	}

	@Test
	void resolveTags_shouldTreatEmptyListAsClearAll() {
		assertThat(inspectionTagLogic.resolveTags(List.of())).isEmpty();
		verify(inspectionTagStore, never()).getOrCreateAll(anyList());
	}

	@Test
	void getInspectionTags_shouldOrderByUsageThenName() {
		InspectionTag han = tag("t1", "한강");
		InspectionTag near = tag("t2", "직주근접");
		InspectionTag park = tag("t3", "공원");
		when(inspectionTagStore.findAllByKeyword(null)).thenReturn(List.of(han, near, park));
		when(inspectionTagStore.countUsageByTagIds(anySet(), isNull())).thenReturn(Map.of("t1", 3, "t2", 5, "t3", 5));

		List<InspectionTagRdo> tags = inspectionTagLogic.getInspectionTags(null, null);

		assertThat(tags).extracting(InspectionTagRdo::getName).containsExactly("공원", "직주근접", "한강");
		assertThat(tags.get(0).getUsageCount()).isEqualTo(5);
	}

	@Test
	void getInspectionTags_shouldDefaultUsageToZero() {
		when(inspectionTagStore.findAllByKeyword("한")).thenReturn(List.of(tag("t1", "한강")));
		when(inspectionTagStore.countUsageByTagIds(anySet(), isNull())).thenReturn(Map.of());

		assertThat(inspectionTagLogic.getInspectionTags("한", null).get(0).getUsageCount()).isZero();
	}

	/**
	 * B-⑨: 임장 태그(#역세권)와 매물 태그(#올수리)가 같은 풀을 공유하지만 용도가 다르다.
	 * scope를 지정하면 그 용도의 사용 빈도만 정렬에 반영되어 순위가 scope별로 달라져야 한다.
	 */
	@Test
	void getInspectionTags_shouldRankDifferentlyPerScope() {
		InspectionTag stationArea = tag("t1", "역세권");
		InspectionTag fullyRenovated = tag("t2", "올수리");
		when(inspectionTagStore.findAllByKeyword(null)).thenReturn(List.of(stationArea, fullyRenovated));
		// 역세권은 임장에서만, 올수리는 매물에서만 쓰였다고 가정한다
		when(inspectionTagStore.countUsageByTagIds(anySet(), eq(InspectionTagScope.VISIT)))
			.thenReturn(Map.of("t1", 4));
		when(inspectionTagStore.countUsageByTagIds(anySet(), eq(InspectionTagScope.PROPERTY)))
			.thenReturn(Map.of("t2", 4));

		List<InspectionTagRdo> visitScoped = inspectionTagLogic.getInspectionTags(null, InspectionTagScope.VISIT);
		List<InspectionTagRdo> propertyScoped = inspectionTagLogic.getInspectionTags(null,
			InspectionTagScope.PROPERTY);

		assertThat(visitScoped).extracting(InspectionTagRdo::getName).containsExactly("역세권", "올수리");
		assertThat(visitScoped.get(0).getUsageCount()).isEqualTo(4);
		assertThat(visitScoped.get(1).getUsageCount()).isZero();

		assertThat(propertyScoped).extracting(InspectionTagRdo::getName).containsExactly("올수리", "역세권");
		assertThat(propertyScoped.get(0).getUsageCount()).isEqualTo(4);
		assertThat(propertyScoped.get(1).getUsageCount()).isZero();
	}
}
