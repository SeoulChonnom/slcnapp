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
		int now = Integer.parseInt(idSequence.getLastId(), 16);

		StringBuilder nextId = new StringBuilder(Integer.toHexString(now + 1));
		idSequence.setLastId(nextId.toString());

		while(nextId.length() < 4 ) {
			nextId.insert(0, "0");
		}
		return idSequence.getName() + '-' + nextId;
	}
}
