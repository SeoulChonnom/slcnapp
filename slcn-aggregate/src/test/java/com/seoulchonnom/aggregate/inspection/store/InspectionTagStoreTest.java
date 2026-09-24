package com.seoulchonnom.aggregate.inspection.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionTagJpo;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionTagJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionTagRepository;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionVisitTagRepository;
import com.seoulchonnom.aggregate.inspection.store.repository.ViewedPropertyTagRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionTag;

class InspectionTagStoreTest {
	private final InspectionTagRepository inspectionTagRepository = mock(InspectionTagRepository.class);
	private final InspectionVisitTagRepository inspectionVisitTagRepository =
		mock(InspectionVisitTagRepository.class);
	private final ViewedPropertyTagRepository viewedPropertyTagRepository =
		mock(ViewedPropertyTagRepository.class);
	private final InspectionTagJpoMapper inspectionTagJpoMapper = new InspectionTagJpoMapper();
	private final InspectionTagStore inspectionTagStore = new InspectionTagStore(inspectionTagRepository,
		inspectionVisitTagRepository, viewedPropertyTagRepository, inspectionTagJpoMapper);

	/**
	 * EntityJpo의 @Id는 생성기 없는 수동 할당이다. id를 비운 채 저장하면 Hibernate가
	 * "Identifier must be manually assigned"로 터져 태그를 새로 만드는 모든 요청이 500이 된다.
	 * 목으로도 저장 인자의 id는 확인할 수 있으므로 여기서 막는다.
	 */
	@Test
	void getOrCreateAll_shouldAssignIdWhenCreatingNewTag() {
		when(inspectionTagRepository.findAllByNameIn(anyList())).thenReturn(List.of());
		when(inspectionTagRepository.saveAndFlush(any(InspectionTagJpo.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		inspectionTagStore.getOrCreateAll(List.of("한강"));

		ArgumentCaptor<InspectionTagJpo> captor = ArgumentCaptor.forClass(InspectionTagJpo.class);
		verify(inspectionTagRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getId()).isNotBlank();
		assertThat(captor.getValue().getName()).isEqualTo("한강");
		assertThat(captor.getValue().getRegisteredTime()).isNotNull();
	}

	@Test
	void getOrCreateAll_shouldReuseExistingTagWithoutInsert() {
		InspectionTagJpo existing = new InspectionTagJpo("한강");
		existing.setId("TAG-01");
		when(inspectionTagRepository.findAllByNameIn(anyList())).thenReturn(List.of(existing));

		List<InspectionTag> result = inspectionTagStore.getOrCreateAll(List.of("한강"));

		assertThat(result).extracting(InspectionTag::getId).containsExactly("TAG-01");
		verify(inspectionTagRepository, never()).saveAndFlush(any());
	}

	@Test
	void getOrCreateAll_shouldAssignDistinctIdsAcrossNewTags() {
		when(inspectionTagRepository.findAllByNameIn(anyList())).thenReturn(List.of());
		when(inspectionTagRepository.saveAndFlush(any(InspectionTagJpo.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		List<InspectionTag> result = inspectionTagStore.getOrCreateAll(List.of("한강", "서울숲", "직주근접"));

		assertThat(result).extracting(InspectionTag::getId).doesNotContainNull().doesNotHaveDuplicates();
		assertThat(result).extracting(InspectionTag::getName).containsExactly("한강", "서울숲", "직주근접");
	}
}
