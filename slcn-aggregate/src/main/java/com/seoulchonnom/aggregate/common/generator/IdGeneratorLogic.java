package com.seoulchonnom.aggregate.common.generator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.common.generator.store.entity.IdSequence;
import com.seoulchonnom.aggregate.common.generator.store.repository.IdSequenceRepository;
import com.seoulchonnom.spec.common.generator.IdGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional
public class IdGeneratorLogic implements IdGenerator {
	private final IdSequenceRepository idSequenceRepository;

	@Override
	public String nextDomainId(String domain) {
		IdSequence now = idSequenceRepository.findByName(domain)
			.orElseThrow(() -> new BadRequestException("ID NOT EXIST"));
		return nextId(now);
	}

	private String nextId(IdSequence idSequence) {
		long now;
		try {
			now = Long.parseLong(idSequence.getLastId(), 16);
		} catch (NumberFormatException | NullPointerException e) {
			throw new BadRequestException("ID FORMAT INVALID");
		}

		StringBuilder nextId = new StringBuilder(Long.toHexString(now + 1));

		while(nextId.length() < 4 ) {
			nextId.insert(0, "0");
		}
		idSequence.setLastId(nextId.toString());
		return idSequence.getName() + '-' + nextId;
	}
}
