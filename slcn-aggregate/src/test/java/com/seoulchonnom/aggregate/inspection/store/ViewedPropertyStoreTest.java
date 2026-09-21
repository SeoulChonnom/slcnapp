package com.seoulchonnom.aggregate.inspection.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.seoulchonnom.aggregate.inspection.exception.ViewedPropertyConflictException;
import com.seoulchonnom.aggregate.inspection.store.mapper.ViewedPropertyJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.ViewedPropertyRepository;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;

class ViewedPropertyStoreTest {
	private final ViewedPropertyRepository viewedPropertyRepository = mock(ViewedPropertyRepository.class);
	private final ViewedPropertyJpoMapper viewedPropertyJpoMapper = new ViewedPropertyJpoMapper();
	private final ViewedPropertyStore viewedPropertyStore = new ViewedPropertyStore(viewedPropertyRepository,
		viewedPropertyJpoMapper);

	private static ViewedProperty property() {
		ViewedProperty property = new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
		property.setId("prop-1");
		return property;
	}

	@Test
	void save_shouldConvertOptimisticLockingFailureToConflict() {
		// EntityJpo의 @Version이 모든 엔티티에 낙관적 잠금을 건다. 두 사용자가 같은 매물을
		// 동시에 저장하면 이 예외가 나야 하고, 500이 아니라 409로 바뀌어야 한다.
		when(viewedPropertyRepository.saveAndFlush(any())).thenThrow(
			new ObjectOptimisticLockingFailureException("ViewedPropertyJpo", "prop-1"));

		assertThatThrownBy(() -> viewedPropertyStore.save(property()))
			.isInstanceOf(ViewedPropertyConflictException.class);
	}

	@Test
	void save_shouldFlushImmediatelyRatherThanDeferringToCommit() {
		ViewedProperty saved = property();
		when(viewedPropertyRepository.saveAndFlush(any())).thenReturn(viewedPropertyJpoMapper.toJpo(saved));

		viewedPropertyStore.save(saved);

		verify(viewedPropertyRepository).saveAndFlush(any());
		verify(viewedPropertyRepository, never()).save(any());
	}

	@Test
	void saveAll_shouldConvertOptimisticLockingFailureToConflict() {
		when(viewedPropertyRepository.saveAllAndFlush(anyList())).thenThrow(
			new ObjectOptimisticLockingFailureException("ViewedPropertyJpo", "prop-1"));

		assertThatThrownBy(() -> viewedPropertyStore.saveAll(List.of(property())))
			.isInstanceOf(ViewedPropertyConflictException.class);
	}

	@Test
	void saveAll_shouldFlushImmediatelyRatherThanDeferringToCommit() {
		ViewedProperty saved = property();
		when(viewedPropertyRepository.saveAllAndFlush(anyList()))
			.thenReturn(List.of(viewedPropertyJpoMapper.toJpo(saved)));

		viewedPropertyStore.saveAll(List.of(saved));

		verify(viewedPropertyRepository).saveAllAndFlush(anyList());
		verify(viewedPropertyRepository, never()).saveAll(anyList());
	}
}
