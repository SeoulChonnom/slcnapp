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
	private static final long MAX_SEQUENCE_VALUE = 0xFFFFL;
	/**
	 * 첫 채번이 DOMAIN-0001이 되도록 0에서 시작한다. 기존 행들과 같은 4자리 16진 표기다.
	 */
	private static final String INITIAL_LAST_ID = "0000";

	private final IdSequenceRepository idSequenceRepository;

	/**
	 * 시퀀스 행이 없으면 만들어 쓴다.
	 *
	 * 예전에는 "ID NOT EXIST"로 거절했는데, 그러면 새 도메인을 추가할 때마다 사람이 id_sequence에
	 * 행을 직접 넣어야 하고 저장소에는 그 행을 만들어 주는 마이그레이션도 부트스트랩도 없었다.
	 * 그래서 새 DB에 배포하면 해당 도메인의 등록 API가 전부 400으로 떨어졌다 — 실제로 임장 기록의
	 * 지역/임장/질문 등록이 이 이유로 동작하지 않았다.
	 */
	@Override
	public String nextDomainId(String domain) {
		return nextId(idSequenceRepository.findByName(domain).orElseGet(() -> createSequence(domain)));
	}

	/**
	 * 만든 뒤 반드시 findByName으로 다시 읽는다. 그래야 PESSIMISTIC_WRITE 락을 잡은 상태의
	 * 영속 엔티티를 얻고, 같은 도메인의 첫 채번이 동시에 들어와도 이후 증가가 직렬화된다.
	 * 여기서 만든 행을 그대로 쓰면 락 없이 증가시키게 되어 같은 ID가 두 번 나갈 수 있다.
	 */
	private IdSequence createSequence(String domain) {
		idSequenceRepository.insertIfAbsent(domain, INITIAL_LAST_ID);
		return idSequenceRepository.findByName(domain)
			.orElseThrow(() -> new BadRequestException("ID NOT EXIST"));
	}

	private String nextId(IdSequence idSequence) {
		long now;
		try {
			now = Long.parseLong(idSequence.getLastId(), 16);
		} catch (NumberFormatException | NullPointerException e) {
			throw new BadRequestException("ID FORMAT INVALID");
		}

		if (now + 1 > MAX_SEQUENCE_VALUE) {
			throw new BadRequestException("ID CAPACITY EXCEEDED");
		}

		String nextId = String.format("%04x", now + 1);
		idSequence.setLastId(nextId);
		return idSequence.getName() + '-' + nextId;
	}
}
