package com.seoulchonnom.aggregate.house.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.house.store.repository.HouseTradeRepository;

class HouseTradeStoreOutlierTest {
	private static final LocalDate SINCE = LocalDate.of(2023, 6, 20);

	private final HouseTradeRepository repository = mock(HouseTradeRepository.class);
	private final HouseTradeStore store = new HouseTradeStore(repository);

	@Test
	void replaceOutliers_shouldClearButNotMarkWhenIdsEmpty() {
		int marked = store.replaceOutliers(SINCE, List.of());

		assertThat(marked).isZero();
		verify(repository).clearOutliers(SINCE);
		verify(repository, never()).markOutliers(any());
	}

	@Test
	void replaceOutliers_shouldSendIdsInChunksOf1000() {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 2500; i++) {
			ids.add("id" + i);
		}
		when(repository.markOutliers(any())).thenReturn(1000, 1000, 500);

		int marked = store.replaceOutliers(SINCE, ids);

		assertThat(marked).isEqualTo(2500);
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(repository, times(3)).markOutliers(captor.capture());
		assertThat(captor.getAllValues().get(0).split(",")).hasSize(1000);
		assertThat(captor.getAllValues().get(2).split(",")).hasSize(500).startsWith("id2000");
	}
}
