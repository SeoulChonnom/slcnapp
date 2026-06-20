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
