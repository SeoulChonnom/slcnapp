package com.seoulchonnom.aggregate.inspection.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.seoulchonnom.aggregate.inspection.exception.InspectionVisitConflictException;
import com.seoulchonnom.aggregate.inspection.store.mapper.InspectionVisitJpoMapper;
import com.seoulchonnom.aggregate.inspection.store.repository.InspectionVisitRepository;
import com.seoulchonnom.spec.inspection.entity.InspectionVisit;

class InspectionVisitStoreTest {
	private final InspectionVisitRepository inspectionVisitRepository = mock(InspectionVisitRepository.class);
	private final InspectionVisitJpoMapper inspectionVisitJpoMapper = new InspectionVisitJpoMapper();
	private final InspectionVisitStore inspectionVisitStore = new InspectionVisitStore(inspectionVisitRepository,
		inspectionVisitJpoMapper);

	private static InspectionVisit visit() {
		InspectionVisit visit = new InspectionVisit("INSPECTION_VISIT-0001", "INSPECTION_AREA-0001",
			LocalDateTime.of(2026, 9, 17, 14, 0));
		return visit;
	}

	@Test
	void save_shouldConvertOptimisticLockingFailureToConflict() {
		// EntityJpo의 @Version이 모든 엔티티에 낙관적 잠금을 건다. 두 사용자가 같은 임장을
		// 동시에 저장하면 이 예외가 나야 하고, 500이 아니라 409로 바뀌어야 한다.
		when(inspectionVisitRepository.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(
			"InspectionVisitJpo", "INSPECTION_VISIT-0001"));

		assertThatThrownBy(() -> inspectionVisitStore.save(visit()))
			.isInstanceOf(InspectionVisitConflictException.class);
	}

	@Test
	void save_shouldFlushImmediatelyRatherThanDeferringToCommit() {
		InspectionVisit saved = visit();
		when(inspectionVisitRepository.saveAndFlush(any())).thenReturn(inspectionVisitJpoMapper.toJpo(saved));

		inspectionVisitStore.save(saved);

		// saveAndFlush를 쓰지 않으면 예외가 트랜잭션 커밋 시점에야 터져 이 메서드 밖에서 500으로 샌다
		verify(inspectionVisitRepository).saveAndFlush(any());
		verify(inspectionVisitRepository, never()).save(any());
	}
}
