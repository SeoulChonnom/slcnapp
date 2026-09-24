package com.seoulchonnom.aggregate.common.generator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.IdSequence;
import com.seoulchonnom.aggregate.common.generator.store.repository.IdSequenceRepository;

class IdGeneratorLogicTest {
	private final IdSequenceRepository idSequenceRepository = mock(IdSequenceRepository.class);
	private final IdGeneratorLogic idGeneratorLogic = new IdGeneratorLogic(idSequenceRepository);

	@Test
	void nextDomainId_shouldPadAndPersistNextHexId() {
		IdSequence idSequence = new IdSequence();
		idSequence.setName("TRIP");
		idSequence.setLastId("000f");
		when(idSequenceRepository.findByName("TRIP")).thenReturn(Optional.of(idSequence));

		String result = idGeneratorLogic.nextDomainId("TRIP");

		assertThat(result).isEqualTo("TRIP-0010");
		assertThat(idSequence.getLastId()).isEqualTo("0010");
	}

	@Test
	void nextDomainId_shouldKeepFourDigitPaddedFormat() {
		IdSequence idSequence = new IdSequence();
		idSequence.setName("TRIP");
		idSequence.setLastId("00ff");
		when(idSequenceRepository.findByName("TRIP")).thenReturn(Optional.of(idSequence));

		String result = idGeneratorLogic.nextDomainId("TRIP");

		assertThat(result).isEqualTo("TRIP-0100");
		assertThat(idSequence.getLastId()).isEqualTo("0100");
	}

	@Test
	void nextDomainId_shouldRejectWhenSequenceExceedsCapacity() {
		IdSequence idSequence = new IdSequence();
		idSequence.setName("TRIP");
		idSequence.setLastId("ffff");
		when(idSequenceRepository.findByName("TRIP")).thenReturn(Optional.of(idSequence));

		assertThatThrownBy(() -> idGeneratorLogic.nextDomainId("TRIP"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("ID CAPACITY EXCEEDED");
	}

	@Test
	void nextDomainId_shouldCreateSequenceWhenMissing() {
		IdSequence created = new IdSequence();
		created.setName("INSPECTION_AREA");
		created.setLastId("0000");
		when(idSequenceRepository.findByName("INSPECTION_AREA"))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(created));

		String result = idGeneratorLogic.nextDomainId("INSPECTION_AREA");

		assertThat(result).isEqualTo("INSPECTION_AREA-0001");
		verify(idSequenceRepository).insertIfAbsent("INSPECTION_AREA", "0000");
	}

	@Test
	void nextDomainId_shouldReadBackWithLockAfterCreatingSequence() {
		IdSequence created = new IdSequence();
		created.setName("INSPECTION_VISIT");
		created.setLastId("0000");
		when(idSequenceRepository.findByName("INSPECTION_VISIT"))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(created));

		idGeneratorLogic.nextDomainId("INSPECTION_VISIT");

		// 두 번째 조회가 없으면 락을 잡지 않은 엔티티를 증가시켜 같은 ID가 두 번 나갈 수 있다.
		verify(idSequenceRepository, times(2)).findByName("INSPECTION_VISIT");
	}

	@Test
	void nextDomainId_shouldRejectInvalidLastId() {
		IdSequence idSequence = new IdSequence();
		idSequence.setName("TRIP");
		idSequence.setLastId("not-hex");
		when(idSequenceRepository.findByName("TRIP")).thenReturn(Optional.of(idSequence));

		assertThatThrownBy(() -> idGeneratorLogic.nextDomainId("TRIP"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("ID FORMAT INVALID");
	}
}
