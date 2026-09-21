package com.seoulchonnom.aggregate.common.generator.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.seoulchonnom.aggregate.common.generator.store.entity.IdSequence;

import jakarta.persistence.LockModeType;

public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<IdSequence> findByName(String name);

	/**
	 * 없을 때만 시퀀스 행을 만든다. save()를 쓰면 같은 도메인의 첫 채번이 동시에 들어올 때
	 * 한쪽이 PK 충돌로 터지고, 그 예외가 트랜잭션을 rollback-only로 만들어 복구 조회조차 못 한다.
	 * ON CONFLICT DO NOTHING이면 예외 없이 끝나고, 상대 트랜잭션이 아직 커밋 전이면
	 * PostgreSQL이 여기서 대기시켜 준다.
	 */
	// clearAutomatically를 켜면 안 된다. 채번은 등록 트랜잭션 한가운데서 일어나는데,
	// 컨텍스트를 비우면 뒤이어 저장할 엔티티가 detach되어 merge가 낙관적 잠금 실패로 떨어진다.
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT INTO slcn.id_sequence (name, last_id) VALUES (:name, :lastId) "
		+ "ON CONFLICT (name) DO NOTHING", nativeQuery = true)
	void insertIfAbsent(@Param("name") String name, @Param("lastId") String lastId);
}
