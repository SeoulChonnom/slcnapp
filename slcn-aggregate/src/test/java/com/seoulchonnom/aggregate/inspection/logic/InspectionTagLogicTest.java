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
		when(inspectionTagStore.countUsageByTagIds(anySet())).thenReturn(Map.of("t1", 3, "t2", 5, "t3", 5));

		List<InspectionTagRdo> tags = inspectionTagLogic.getInspectionTags(null);

		assertThat(tags).extracting(InspectionTagRdo::getName).containsExactly("공원", "직주근접", "한강");
		assertThat(tags.get(0).getUsageCount()).isEqualTo(5);
	}

	@Test
	void getInspectionTags_shouldDefaultUsageToZero() {
		when(inspectionTagStore.findAllByKeyword("한")).thenReturn(List.of(tag("t1", "한강")));
		when(inspectionTagStore.countUsageByTagIds(anySet())).thenReturn(Map.of());

		assertThat(inspectionTagLogic.getInspectionTags("한").get(0).getUsageCount()).isZero();
	}
}
